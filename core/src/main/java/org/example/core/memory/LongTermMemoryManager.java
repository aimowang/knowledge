package org.example.core.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.model.LongTermMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 长期记忆管理器（基于文件存储 + 向量检索）
 * 支持持久化、向量检索、记忆合并和遗忘
 */
@Slf4j
@Component
public class LongTermMemoryManager {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmbeddingModel embeddingModel;
    
    /**
     * 用户ID -> 记忆列表
     */
    private final Map<String, List<LongTermMemory>> userMemories = new ConcurrentHashMap<>();
    
    /**
     * 用户ID -> 记忆向量缓存 (memoryId -> embedding)
     */
    private final Map<String, Map<String, float[]>> memoryEmbeddings = new ConcurrentHashMap<>();
    
    /**
     * 记忆存储路径
     */
    private static final String MEMORY_DIR = "data/long-term-memory";
    
    /**
     * 记忆合并阈值（余弦相似度）
     */
    private static final double MERGE_THRESHOLD = 0.85;
    
    public LongTermMemoryManager(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    @PostConstruct
    public void init() {
        try {
            Path dir = Paths.get(MEMORY_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("创建长期记忆目录: {}", MEMORY_DIR);
            }
            loadAllMemories();
            
            // 启动定期清理任务
            scheduleMemoryCleanup();
        } catch (IOException e) {
            log.error("初始化长期记忆失败", e);
        }
    }

    private void scheduleMemoryCleanup() {
        // 定期清理
    }

    /**
     * 添加记忆（自动检查合并）
     */
    public void addMemory(LongTermMemory memory) {
        if (memory.getId() == null || memory.getId().isEmpty()) {
            memory.setId(UUID.randomUUID().toString());
        }
        
        List<LongTermMemory> memories = userMemories.computeIfAbsent(memory.getUserId(), k -> new ArrayList<>());
        
        // 1. 检查是否可以合并到现有记忆
        LongTermMemory existingMemory = findSimilarMemory(memory.getUserId(), memory);
        if (existingMemory != null) {
            mergeMemories(existingMemory, memory);
            saveUserMemories(memory.getUserId());
            log.info("记忆合并: {} + {}", existingMemory.getContent(), memory.getContent());
            return;
        }
        
        // 2. 添加新记忆
        memories.add(memory);
        saveUserMemories(memory.getUserId());
        log.info("为用户 {} 添加长期记忆: {}", memory.getUserId(), memory.getContent());
    }
    
    /**
     * 查找相似的记忆
     */
    private LongTermMemory findSimilarMemory(String userId, LongTermMemory newMemory) {
        List<LongTermMemory> memories = userMemories.getOrDefault(userId, List.of());
        
        try {
            float[] newEmbedding = embedText(newMemory.getContent() + " " + 
                (newMemory.getKeywords() != null ? newMemory.getKeywords() : ""));
            
            for (LongTermMemory existing : memories) {
                // 只检查相同类型的记忆
                if (existing.getType() != newMemory.getType()) {
                    continue;
                }
                
                float[] existingEmbedding = getOrCreateEmbedding(userId, existing);
                if (existingEmbedding != null) {
                    double similarity = cosineSimilarity(newEmbedding, existingEmbedding);
                    if (similarity >= MERGE_THRESHOLD) {
                        log.debug("找到相似记忆: 相似度={:.2f}, 现有={}, 新={}", 
                            similarity, existing.getContent(), newMemory.getContent());
                        return existing;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查找相似记忆失败", e);
        }
        
        return null;
    }
    
    /**
     * 合并两个相似的记忆
     */
    private void mergeMemories(LongTermMemory existing, LongTermMemory newMemory) {
        // 1. 保留更重要的内容
        if (newMemory.getImportance() > existing.getImportance()) {
            existing.setContent(newMemory.getContent());
            existing.setImportance(newMemory.getImportance());
        }
        
        // 2. 合并关键词（去重）
        String existingKeywords = existing.getKeywords() != null ? existing.getKeywords() : "";
        String newKeywords = newMemory.getKeywords() != null ? newMemory.getKeywords() : "";
        
        Set<String> keywordSet = new LinkedHashSet<>();
        if (!existingKeywords.isEmpty()) {
            keywordSet.addAll(Arrays.asList(existingKeywords.split(",")));
        }
        if (!newKeywords.isEmpty()) {
            keywordSet.addAll(Arrays.asList(newKeywords.split(",")));
        }
        existing.setKeywords(String.join(",", keywordSet));
        
        // 3. 提升重要性（最多到10）
        existing.setImportance(Math.min(10, existing.getImportance() + 1));
        
        // 4. 更新最后访问时间
        existing.setLastAccessedAt(java.time.LocalDateTime.now());
        
        log.info("记忆合并完成: 内容={}, 关键词={}, 重要性={}", 
            existing.getContent(), existing.getKeywords(), existing.getImportance());
    }
    
    /**
     * 根据查询检索记忆（向量相似度检索）
     */
    public List<LongTermMemory> searchMemories(String userId, String query, int topK) {
        List<LongTermMemory> memories = userMemories.getOrDefault(userId, List.of());
        
        if (memories.isEmpty() || query == null || query.isEmpty()) {
            return List.of();
        }
        
        try {
            // 1. 将查询向量化
            float[] queryEmbedding = embedText(query);
            
            // 2. 计算每个记忆的相似度
            List<MemoryScore> scoredMemories = new ArrayList<>();
            for (LongTermMemory memory : memories) {
                float[] memoryEmbedding = getOrCreateEmbedding(userId, memory);
                if (memoryEmbedding != null) {
                    double similarity = cosineSimilarity(queryEmbedding, memoryEmbedding);
                    scoredMemories.add(new MemoryScore(memory, similarity));
                }
            }
            
            // 3. 按相似度排序，返回 Top-K
            return scoredMemories.stream()
                    .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
                    .limit(topK)
                    .peek(ms -> ms.memory.incrementAccess())
                    .map(ms -> ms.memory)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("向量检索失败，降级为关键词匹配", e);
            // 降级：使用关键词匹配
            return keywordSearch(userId, query, topK);
        }
    }
    
    /**
     * 关键词匹配（降级方案）
     */
    private List<LongTermMemory> keywordSearch(String userId, String query, int topK) {
        List<LongTermMemory> memories = userMemories.getOrDefault(userId, List.of());
        String lowerQuery = query.toLowerCase();
        
        return memories.stream()
                .filter(m -> {
                    String content = m.getContent().toLowerCase();
                    String keywords = m.getKeywords() != null ? m.getKeywords().toLowerCase() : "";
                    return content.contains(lowerQuery) || keywords.contains(lowerQuery);
                })
                .sorted((m1, m2) -> {
                    int score1 = m1.getImportance() * 10 + m1.getAccessCount();
                    int score2 = m2.getImportance() * 10 + m2.getAccessCount();
                    return Integer.compare(score2, score1);
                })
                .limit(topK)
                .peek(m -> m.incrementAccess())
                .collect(Collectors.toList());
    }
    
    /**
     * 获取或创建记忆的向量表示
     */
    private float[] getOrCreateEmbedding(String userId, LongTermMemory memory) {
        Map<String, float[]> userEmbeddings = memoryEmbeddings.computeIfAbsent(userId, k -> new HashMap<>());
        
        return userEmbeddings.computeIfAbsent(memory.getId(), id -> {
            try {
                // 结合内容和关键词生成向量
                String text = memory.getContent() + " " + (memory.getKeywords() != null ? memory.getKeywords() : "");
                return embedText(text);
            } catch (Exception e) {
                log.warn("生成记忆向量失败: {}", memory.getId(), e);
                return null;
            }
        });
    }
    
    /**
     * 文本向量化
     */
    private float[] embedText(String text) {
        float[] embeddings = embeddingModel.embed(text);
        float[] result = new float[embeddings.length];
        for (int i = 0; i < embeddings.length; i++) {
            result[i] = embeddings[i];
        }
        return result;
    }
    
    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length != vec2.length) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * 记忆评分内部类
     */
    private static class MemoryScore {
        final LongTermMemory memory;
        final double similarity;
        
        MemoryScore(LongTermMemory memory, double similarity) {
            this.memory = memory;
            this.similarity = similarity;
        }
    }
    
    /**
     * 获取用户的所有记忆
     */
    public List<LongTermMemory> getUserMemories(String userId) {
        return List.copyOf(userMemories.getOrDefault(userId, List.of()));
    }
    
    /**
     * 删除记忆
     */
    public boolean deleteMemory(String userId, String memoryId) {
        List<LongTermMemory> memories = userMemories.get(userId);
        if (memories == null) {
            return false;
        }
        
        boolean removed = memories.removeIf(m -> m.getId().equals(memoryId));
        if (removed) {
            saveUserMemories(userId);
            log.info("删除用户 {} 的记忆: {}", userId, memoryId);
        }
        return removed;
    }
    
    /**
     * 清空用户的所有记忆
     */
    public void clearUserMemories(String userId) {
        userMemories.remove(userId);
        
        // 删除文件
        try {
            Path file = Paths.get(MEMORY_DIR, userId + ".json");
            if (Files.exists(file)) {
                Files.delete(file);
            }
            log.info("清空用户 {} 的所有长期记忆", userId);
        } catch (IOException e) {
            log.error("删除记忆文件失败", e);
        }
    }
    
    /**
     * 从文件加载所有记忆
     */
    private void loadAllMemories() {
        try {
            Path dir = Paths.get(MEMORY_DIR);
            if (!Files.exists(dir)) {
                return;
            }
            
            Files.list(dir)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(this::loadUserMemories);
            
            log.info("加载长期记忆完成，共 {} 个用户", userMemories.size());
        } catch (IOException e) {
            log.error("加载长期记忆失败", e);
        }
    }
    
    /**
     * 加载单个用户的记忆
     */
    private void loadUserMemories(Path filePath) {
        try {
            String fileName = filePath.getFileName().toString();
            String userId = fileName.replace(".json", "");
            
            String json = Files.readString(filePath);
            List<LongTermMemory> memories = objectMapper.readValue(
                json, 
                new TypeReference<List<LongTermMemory>>() {}
            );
            
            userMemories.put(userId, memories);
            log.debug("加载用户 {} 的 {} 条记忆", userId, memories.size());
        } catch (IOException e) {
            log.error("加载用户记忆失败: {}", filePath, e);
        }
    }
    
    /**
     * 保存用户记忆到文件
     */
    private void saveUserMemories(String userId) {
        try {
            Path dir = Paths.get(MEMORY_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            
            Path file = dir.resolve(userId + ".json");
            List<LongTermMemory> memories = userMemories.get(userId);
            
            if (memories != null && !memories.isEmpty()) {
                String json = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(memories);
                Files.writeString(file, json);
            }
        } catch (IOException e) {
            log.error("保存用户记忆失败: {}", userId, e);
        }
    }
}
