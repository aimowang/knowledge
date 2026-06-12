package org.example.core.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.core.repository.LongTermMemoryRepository;
import org.example.model.LongTermMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 长期记忆管理器（基于 MySQL + 向量检索）
 */
@Slf4j
@Component
public class LongTermMemoryManager {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmbeddingModel embeddingModel;
    private final LongTermMemoryRepository repository;
    
    /**
     * 用户ID -> 记忆向量缓存 (memoryId -> embedding)
     */
    private final Map<String, Map<String, float[]>> memoryEmbeddings = new ConcurrentHashMap<>();
    
    /**
     * 记忆合并阈值（余弦相似度）
     */
    private static final double MERGE_THRESHOLD = 0.85;
    
    public LongTermMemoryManager(EmbeddingModel embeddingModel, LongTermMemoryRepository repository) {
        this.embeddingModel = embeddingModel;
        this.repository = repository;
    }
    
    @PostConstruct
    public void init() {
        log.info("长期记忆管理器初始化完成，使用 MySQL 持久化");
        scheduleMemoryCleanup();
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
        
        // 1. 检查是否可以合并到现有记忆
        LongTermMemory existingMemory = findSimilarMemory(memory.getUserId(), memory);
        if (existingMemory != null) {
            mergeMemories(existingMemory, memory);
            saveToDatabase(existingMemory);
            log.info("记忆合并: {} + {}", existingMemory.getContent(), memory.getContent());
            return;
        }
        
        // 2. 添加新记忆
        saveToDatabase(memory);
        log.info("为用户 {} 添加长期记忆: {}", memory.getUserId(), memory.getContent());
    }
    
    private void saveToDatabase(LongTermMemory memory) {
        org.example.model.entity.LongTermMemoryEntity entity = new org.example.model.entity.LongTermMemoryEntity();
        entity.setId(memory.getId());
        entity.setUserId(memory.getUserId());
        entity.setType(memory.getType().name());
        entity.setContent(memory.getContent());
        entity.setKeywords(memory.getKeywords());
        entity.setImportance(memory.getImportance());
        entity.setAccessCount(memory.getAccessCount());
        entity.setCreatedAt(memory.getCreatedAt());
        entity.setLastAccessedAt(memory.getLastAccessedAt());
        repository.save(entity);
    }
    
    /**
     * 查找相似的记忆
     */
    private LongTermMemory findSimilarMemory(String userId, LongTermMemory newMemory) {
        List<LongTermMemory> memories = getUserMemoriesFromDb(userId);
        
        try {
            float[] newEmbedding = embedText(newMemory.getContent() + " " + 
                (newMemory.getKeywords() != null ? newMemory.getKeywords() : ""));
            
            for (LongTermMemory existing : memories) {
                if (existing.getType() != newMemory.getType()) {
                    continue;
                }
                
                float[] existingEmbedding = getOrCreateEmbedding(userId, existing);
                if (existingEmbedding != null) {
                    double similarity = cosineSimilarity(newEmbedding, existingEmbedding);
                    if (similarity >= MERGE_THRESHOLD) {
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
        List<LongTermMemory> memories = getUserMemoriesFromDb(userId);
        
        if (memories.isEmpty() || query == null || query.isEmpty()) {
            return List.of();
        }
        
        try {
            float[] queryEmbedding = embedText(query);
            List<MemoryScore> scoredMemories = new ArrayList<>();
            for (LongTermMemory memory : memories) {
                float[] memoryEmbedding = getOrCreateEmbedding(userId, memory);
                if (memoryEmbedding != null) {
                    double similarity = cosineSimilarity(queryEmbedding, memoryEmbedding);
                    scoredMemories.add(new MemoryScore(memory, similarity));
                }
            }
            
            return scoredMemories.stream()
                    .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
                    .limit(topK)
                    .peek(ms -> ms.memory.incrementAccess())
                    .map(ms -> ms.memory)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("向量检索失败，降级为关键词匹配", e);
            return keywordSearch(memories, query, topK);
        }
    }
    
    /**
     * 关键词匹配（降级方案）
     */
    private List<LongTermMemory> keywordSearch(List<LongTermMemory> memories, String query, int topK) {
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
        return getUserMemoriesFromDb(userId);
    }
    
    /**
     * 获取相关的长期记忆（基于问题内容）
     * 这是编排器调用的方法，内部调用 searchMemories
     */
    public List<LongTermMemory> getRelevantMemories(String userId, String question) {
        if (userId == null || question == null || question.isEmpty()) {
            return List.of();
        }
        
        // 使用向量检索获取最相关的 top 5 条记忆
        return searchMemories(userId, question, 5);
    }
    
    private List<LongTermMemory> getUserMemoriesFromDb(String userId) {
        return repository.findByUserId(userId).stream()
                .map(this::convertToModel)
                .collect(Collectors.toList());
    }
    
    private LongTermMemory convertToModel(org.example.model.entity.LongTermMemoryEntity entity) {
        LongTermMemory memory = new LongTermMemory(entity.getUserId(), 
                LongTermMemory.MemoryType.valueOf(entity.getType()), 
                entity.getContent(), 
                entity.getKeywords());
        memory.setId(entity.getId());
        memory.setImportance(entity.getImportance());
        memory.setAccessCount(entity.getAccessCount());
        memory.setCreatedAt(entity.getCreatedAt());
        memory.setLastAccessedAt(entity.getLastAccessedAt());
        return memory;
    }
    
    /**
     * 删除记忆
     */
    public boolean deleteMemory(String userId, String memoryId) {
        if (repository.findById(memoryId).isPresent()) {
            repository.deleteById(memoryId);
            log.info("删除用户 {} 的记忆: {}", userId, memoryId);
            return true;
        }
        return false;
    }
    
    /**
     * 清空用户的所有记忆
     */
    public void clearUserMemories(String userId) {
        repository.deleteByUserId(userId);
        memoryEmbeddings.remove(userId);
        log.info("清空用户 {} 的所有长期记忆", userId);
    }
    
    /**
     * 从数据库加载所有记忆
     */
    private void loadAllMemories() {
        log.info("从 MySQL 加载长期记忆完成，共 {} 条", repository.count());
    }
}
