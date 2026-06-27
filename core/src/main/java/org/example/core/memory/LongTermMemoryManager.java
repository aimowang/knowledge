package org.example.core.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.core.repository.LongTermMemoryRepository;
import org.example.model.LongTermMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 长期记忆管理器（基于 MySQL + Milvus 向量检索）
 *
 * <p>记忆文本存储在 MySQL，向量存储在 Milvus（ai_memory_vectors 集合）。
 * 查询时优先使用 Milvus 语义搜索，失败时降级到关键词匹配。
 */
@Slf4j
@Component
public class LongTermMemoryManager {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmbeddingModel embeddingModel;
    private final LongTermMemoryRepository repository;
    private final VectorStore memoryVectorStore;

    private static final double MERGE_THRESHOLD = 0.85;

    public LongTermMemoryManager(EmbeddingModel embeddingModel,
                                  LongTermMemoryRepository repository,
                                  VectorStore memoryVectorStore) {
        this.embeddingModel = embeddingModel;
        this.repository = repository;
        this.memoryVectorStore = memoryVectorStore;
    }

    @PostConstruct
    public void init() {
        log.info("长期记忆管理器初始化完成，使用 MySQL + Milvus 向量检索");
    }

    /**
     * 添加记忆（自动检查合并 + 写入 Milvus 向量）。
     */
    public void addMemory(LongTermMemory memory) {
        if (memory.getId() == null || memory.getId().isEmpty()) {
            memory.setId(UUID.randomUUID().toString());
        }

        LongTermMemory existingMemory = findSimilarMemory(memory.getUserId(), memory);
        if (existingMemory != null) {
            mergeMemories(existingMemory, memory);
            saveToDatabase(existingMemory);
            saveToVectorStore(existingMemory);
            log.info("记忆合并: {}", existingMemory.getContent());
            return;
        }

        saveToDatabase(memory);
        saveToVectorStore(memory);
        log.info("为用户 {} 添加长期记忆: {}", memory.getUserId(), memory.getContent());
    }

    private void saveToDatabase(LongTermMemory memory) {
        var entity = new org.example.model.entity.LongTermMemoryEntity();
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
     * 将记忆向量写入 Milvus。
     */
    private void saveToVectorStore(LongTermMemory memory) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("memoryId", memory.getId());
            metadata.put("userId", memory.getUserId());
            metadata.put("type", memory.getType().name());
            metadata.put("importance", memory.getImportance());

            Document doc = new Document(memory.getContent(), memory.getId(), metadata);
            memoryVectorStore.add(List.of(doc));
        } catch (Exception e) {
            log.warn("记忆向量写入 Milvus 失败: {}", e.getMessage());
        }
    }

    /**
     * 查找相似的现有记忆。
     */
    private LongTermMemory findSimilarMemory(String userId, LongTermMemory newMemory) {
        try {
            List<Document> results = memoryVectorStore.similaritySearch(
                    SearchRequest.builder().query(newMemory.getContent()).topK(5).build());
            for (Document doc : results) {
                String type = doc.getMetadata() != null ? (String) doc.getMetadata().get("type") : "";
                if (type.equals(newMemory.getType().name())) {
                    String memoryId = doc.getMetadata() != null ? (String) doc.getMetadata().get("memoryId") : null;
                    if (memoryId != null) {
                        var entity = repository.findById(memoryId);
                        if (entity.isPresent()) {
                            return convertToModel(entity.get());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Milvus 相似搜索失败，降级到余弦扫描: {}", e.getMessage());
        }
        // 降级：内存余弦相似度
        List<LongTermMemory> memories = getUserMemoriesFromDb(userId);
        try {
            float[] newEmb = embedText(newMemory.getContent());
            for (LongTermMemory existing : memories) {
                if (existing.getType() != newMemory.getType()) continue;
                if (cosineSimilarity(newEmb, embedText(existing.getContent())) >= MERGE_THRESHOLD) {
                    return existing;
                }
            }
        } catch (Exception e) {
            log.warn("余弦相似度比较失败", e);
        }
        return null;
    }

    private void mergeMemories(LongTermMemory existing, LongTermMemory newMemory) {
        if (newMemory.getImportance() > existing.getImportance()) {
            existing.setContent(newMemory.getContent());
            existing.setImportance(newMemory.getImportance());
        }
        String ek = existing.getKeywords() != null ? existing.getKeywords() : "";
        String nk = newMemory.getKeywords() != null ? newMemory.getKeywords() : "";
        Set<String> kw = new LinkedHashSet<>();
        if (!ek.isEmpty()) kw.addAll(Arrays.asList(ek.split(",")));
        if (!nk.isEmpty()) kw.addAll(Arrays.asList(nk.split(",")));
        existing.setKeywords(String.join(",", kw));
        existing.setImportance(Math.min(10, existing.getImportance() + 1));
        existing.setLastAccessedAt(java.time.LocalDateTime.now());
    }

    /**
     * 根据查询检索记忆（主路径: Milvus 语义搜索, 降级: 关键词匹配）。
     */
    public List<LongTermMemory> searchMemories(String userId, String query, int topK) {
        if (query == null || query.isEmpty()) return List.of();

        try {
            List<Document> results = memoryVectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(topK * 2).build());
            return results.stream()
                    .filter(doc -> doc.getMetadata() != null
                            && userId.equals(doc.getMetadata().get("userId")))
                    .map(doc -> {
                        String memoryId = (String) doc.getMetadata().get("memoryId");
                        if (memoryId != null) {
                            var entity = repository.findById(memoryId);
                            return entity.map(this::convertToModel).orElse(null);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .limit(topK)
                    .peek(m -> m.incrementAccess())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Milvus 向量检索失败，降级到关键词: {}", e.getMessage());
            return keywordSearch(getUserMemoriesFromDb(userId), query, topK);
        }
    }

    private List<LongTermMemory> keywordSearch(List<LongTermMemory> memories, String query, int topK) {
        String lowerQuery = query.toLowerCase();
        return memories.stream()
                .filter(m -> {
                    String c = m.getContent().toLowerCase();
                    String k = m.getKeywords() != null ? m.getKeywords().toLowerCase() : "";
                    return c.contains(lowerQuery) || k.contains(lowerQuery);
                })
                .sorted((m1, m2) -> Integer.compare(
                    m2.getImportance() * 10 + m2.getAccessCount(),
                    m1.getImportance() * 10 + m1.getAccessCount()))
                .limit(topK)
                .peek(m -> m.incrementAccess())
                .collect(Collectors.toList());
    }

    private float[] embedText(String text) {
        float[] embeddings = embeddingModel.embed(text);
        float[] result = new float[embeddings.length];
        System.arraycopy(embeddings, 0, result, 0, embeddings.length);
        return result;
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) return 0.0;
        double dot = 0.0, n1 = 0.0, n2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            n1 += v1[i] * v1[i];
            n2 += v2[i] * v2[i];
        }
        return (n1 == 0 || n2 == 0) ? 0.0 : dot / (Math.sqrt(n1) * Math.sqrt(n2));
    }

    public List<LongTermMemory> getRelevantMemories(String userId, String question) {
        if (userId == null || question == null || question.isEmpty()) return List.of();
        return searchMemories(userId, question, 5);
    }

    public List<LongTermMemory> getUserMemories(String userId) {
        return getUserMemoriesFromDb(userId);
    }

    private List<LongTermMemory> getUserMemoriesFromDb(String userId) {
        return repository.findByUserId(userId).stream()
                .map(this::convertToModel)
                .collect(Collectors.toList());
    }

    private LongTermMemory convertToModel(org.example.model.entity.LongTermMemoryEntity entity) {
        LongTermMemory memory = new LongTermMemory(entity.getUserId(),
                LongTermMemory.MemoryType.valueOf(entity.getType()),
                entity.getContent(), entity.getKeywords());
        memory.setId(entity.getId());
        memory.setImportance(entity.getImportance());
        memory.setAccessCount(entity.getAccessCount());
        memory.setCreatedAt(entity.getCreatedAt());
        memory.setLastAccessedAt(entity.getLastAccessedAt());
        return memory;
    }

    public boolean deleteMemory(String userId, String memoryId) {
        if (repository.findById(memoryId).isPresent()) {
            repository.deleteById(memoryId);
            log.info("删除用户 {} 的记忆: {}", userId, memoryId);
            return true;
        }
        return false;
    }

    public void clearUserMemories(String userId) {
        repository.deleteByUserId(userId);
        log.info("清空用户 {} 的所有长期记忆", userId);
    }
}
