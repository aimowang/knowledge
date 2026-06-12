package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.handler.ComplexRAGHandler;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.model.enums.AssessmentEnum;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * CRAG（Corrective RAG）处理阶段
 * 评估检索质量并根据评估结果采取不同策略：
 * - CORRECT: 直接使用检索结果
 * - AMBIGUOUS: 知识精炼（二次检索或提取关键片段）
 * - INCORRECT: 切换到外部搜索
 */
@Slf4j
public class CragStage implements PipelineStage {
    
    private final ComplexRAGHandler cragHandler;
    
    public CragStage(ComplexRAGHandler cragHandler) {
        this.cragHandler = cragHandler;
    }
    
    @Override
    public void process(RagContext context) {
        List<Document> docs = context.getDocuments();
        
        if (docs == null || docs.isEmpty()) {
            log.debug("无文档，跳过 CRAG 处理");
            return;
        }
        
        String query = context.getCurrentQuery();
        
        log.debug("开始 CRAG 评估 - 文档数: {}", docs.size());
        
        try {
            // 执行 CRAG 处理
            List<Document> correctedDocs = cragHandler.handle(query, docs);
            
            if (correctedDocs != null && !correctedDocs.isEmpty()) {
                context.setDocuments(correctedDocs);
                log.info("CRAG 处理完成: {} -> {} 个文档", docs.size(), correctedDocs.size());
            } else {
                log.warn("CRAG 处理后文档为空，保留原结果");
            }
            
        } catch (Exception e) {
            log.error("CRAG 处理失败，保留原结果: {}", e.getMessage());
            // 不抛出异常，继续使用原结果
        }
    }
    
    @Override
    public String getName() {
        return "CragStage";
    }
    
    @Override
    public boolean shouldSkip(RagContext context) {
        // 如果没有配置 CRAG Handler 或文档为空，跳过
        return cragHandler == null || 
               context.getDocuments() == null || 
               context.getDocuments().isEmpty();
    }
}
