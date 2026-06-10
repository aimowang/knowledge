package org.example.core.rerank;

import org.example.core.util.CosUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 手动实现MMR 重排序
 */
@Component
public class MMRReranker implements ReRanker {
    private final EmbeddingModel embeddingModel;

    public MMRReranker(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 重排序入口方法
     * @param documents 待重排序的文档列表
     * @param query 查询文本
     * @param k 返回的文档数量
     * @return 重排序后的文档列表
     */
    public List<Document> rerank(List<Document> documents, String query, int k, String... args) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 1. Query 向量化
        float[] queryEmbedding = embeddingModel.embed(query);
        
        // 2. Documents 向量化
        float[][] docEmbeddings = new float[documents.size()][];
        for (int i = 0; i < documents.size(); i++) {
            docEmbeddings[i] = embeddingModel.embed(documents.get(i).getText());
        }
        
        // 3. 执行 MMR 重排序
        return rerank(documents, queryEmbedding, docEmbeddings, k, Double.parseDouble(args[0]));
    }
    
    /**
     * MMR 核心算法
     * @param candidates 候选文档列表
     * @param queryEmbedding 查询向量
     * @param docEmbeddings 文档向量数组
     * @param k 返回的文档数量
     * @param lambda MMR 平衡参数
     * @return 重排序后的文档列表
     */
    public List<Document> rerank(List<Document> candidates,
                                        float[] queryEmbedding,
                                        float[][] docEmbeddings,
                                        int k, double lambda) {
        // 确保 k 不超过候选文档数量
        k = Math.min(k, candidates.size());
        
        List<Integer> selected = new ArrayList<>();
        
        // 选择第一个最相关的文档
        double bestRel = -1;
        int firstIdx = 0;
        for (int i = 0; i < candidates.size(); i++) {
            double rel = CosUtil.cosine(queryEmbedding, docEmbeddings[i]);
            if (rel > bestRel) {
                bestRel = rel;
                firstIdx = i;
            }
        }
        selected.add(firstIdx);
        
        // 依次选择剩余的 k-1 个文档
        for (int i = 1; i < k; i++) {
            double bestScore = -Double.MAX_VALUE;
            int bestIdx = -1;
            
            for (int j = 0; j < candidates.size(); j++) {
                if (selected.contains(j)) continue;
                
                // 计算与 query 的相关性
                double rel = CosUtil.cosine(queryEmbedding, docEmbeddings[j]);
                
                // 计算与已选文档的最大相似度（冗余度）
                int finalJ = j;
                double red = selected.stream()
                        .mapToDouble(s -> CosUtil.cosine(docEmbeddings[finalJ], docEmbeddings[s]))
                        .max().orElse(0);
                
                // MMR 评分公式：λ * Rel - (1-λ) * Red
                double mmr = lambda * rel - (1 - lambda) * red;
                
                if (mmr > bestScore) {
                    bestScore = mmr;
                    bestIdx = j;
                }
            }
            
            if (bestIdx != -1) {
                selected.add(bestIdx);
            }
        }
        
        return selected.stream().map(candidates::get).collect(Collectors.toList());
    }

}
