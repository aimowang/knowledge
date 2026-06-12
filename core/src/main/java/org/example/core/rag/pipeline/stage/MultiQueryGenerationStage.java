package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.core.rag.strategy.impl.MultiQueryGenerator;

import java.util.List;

/**
 * 多查询生成阶段
 * 为复杂问题生成多个变体查询，提升召回率
 */
@Slf4j
public class MultiQueryGenerationStage implements PipelineStage {
    
    private final MultiQueryGenerator multiQueryGenerator;
    
    public MultiQueryGenerationStage(MultiQueryGenerator multiQueryGenerator) {
        this.multiQueryGenerator = multiQueryGenerator;
    }
    
    @Override
    public void process(RagContext context) {
        if (multiQueryGenerator == null) {
            log.debug("MultiQueryGenerator 未配置，跳过多查询生成");
            return;
        }
        
        // 只对复杂问题生成多查询
        if (!multiQueryGenerator.shouldUseMultiQuery(context.getComplexity())) {
            log.debug("非复杂问题，跳过多查询生成");
            return;
        }
        
        String query = context.getCurrentQuery();
        
        if (query == null || query.isEmpty()) {
            log.debug("无增强查询，跳过多查询生成");
            return;
        }
        
        // 生成多查询
        List<String> multiQueries = multiQueryGenerator.generate(query, context);
        
        if (!multiQueries.isEmpty()) {
            context.setMultiQueries(multiQueries);
            log.info("多查询生成完成 - 生成了 {} 个变体查询", multiQueries.size());
        } else {
            log.debug("未生成多查询");
        }
    }
    
    @Override
    public String getName() {
        return "MultiQueryGenerationStage";
    }
    
    @Override
    public boolean shouldSkip(RagContext context) {
        // 如果未配置生成器或不是复杂问题，跳过
        return multiQueryGenerator == null || 
               !multiQueryGenerator.shouldUseMultiQuery(context.getComplexity());
    }
}
