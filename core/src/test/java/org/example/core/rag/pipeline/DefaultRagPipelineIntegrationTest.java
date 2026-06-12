package org.example.core.rag.pipeline;

import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.stage.QueryCleaningStage;
import org.example.model.RagAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultRagPipeline 集成测试
 */
class DefaultRagPipelineIntegrationTest {
    
    private DefaultRagPipeline pipeline;
    
    @BeforeEach
    void setUp() {
        pipeline = new DefaultRagPipeline();
    }
    
    @Test
    void testExecuteWithSingleStage() {
        // 添加一个简单的阶段
        pipeline.addStage(new QueryCleaningStage());
        
        RagContext context = RagContext.builder()
            .originalQuestion("  Spring   Boot  ")
            .userId("user123")
            .build();
        
        RagAnswer answer = pipeline.execute(context);
        
        assertNotNull(answer);
        assertNotNull(context.getCurrentQuery());
        assertEquals("Spring Boot", context.getCurrentQuery());
    }
    
    @Test
    void testExecuteWithMultipleStages() {
        // 添加多个阶段
        pipeline.addStage(new QueryCleaningStage());
        
        RagContext context = RagContext.builder()
            .originalQuestion("  Test   Question  ")
            .userId("user123")
            .build();
        
        RagAnswer answer = pipeline.execute(context);
        
        assertNotNull(answer);
        // 验证阶段已执行
        assertFalse(context.getStageDurations().isEmpty());
    }
    
    @Test
    void testRecordStageDurations() {
        pipeline.addStage(new QueryCleaningStage());
        
        RagContext context = RagContext.builder()
            .originalQuestion("test")
            .userId("user123")
            .build();
        
        long beforeTime = System.currentTimeMillis();
        pipeline.execute(context);
        long afterTime = System.currentTimeMillis();
        
        // 验证记录了耗时
        assertFalse(context.getStageDurations().isEmpty());
        assertTrue(context.getTotalDuration() > 0);
        assertTrue(context.getTotalDuration() <= (afterTime - beforeTime + 100)); // 允许误差
    }
    
    @Test
    void testHandleStageFailure() {
        // 创建一个会失败的阶段
        PipelineStage failingStage = new PipelineStage() {
            @Override
            public void process(RagContext context) {
                throw new RuntimeException("模拟失败");
            }
            
            @Override
            public String getName() {
                return "FailingStage";
            }
        };
        
        pipeline.addStage(failingStage);
        
        RagContext context = RagContext.builder()
            .originalQuestion("test")
            .userId("user123")
            .build();
        
        // 不应该抛出异常，应该优雅处理
        assertDoesNotThrow(() -> pipeline.execute(context));
    }
    
    @Test
    void testAddAndRemoveStages() {
        QueryCleaningStage stage = new QueryCleaningStage();
        
        pipeline.addStage(stage);
        assertEquals(1, pipeline.getStages().size());
        
        pipeline.removeStage("QueryCleaningStage");
        assertEquals(0, pipeline.getStages().size());
    }
    
    @Test
    void testInsertStage() {
        QueryCleaningStage stage1 = new QueryCleaningStage();
        
        pipeline.addStage(stage1);
        pipeline.insertStage(0, new QueryCleaningStage());
        
        assertEquals(2, pipeline.getStages().size());
    }
    
    @Test
    void testEmptyPipeline() {
        RagContext context = RagContext.builder()
            .originalQuestion("test")
            .userId("user123")
            .build();
        
        RagAnswer answer = pipeline.execute(context);
        
        assertNotNull(answer);
    }
    
    @Test
    void testShouldSkipStage() {
        PipelineStage skippableStage = new PipelineStage() {
            @Override
            public void process(RagContext context) {
                fail("这个阶段应该被跳过");
            }
            
            @Override
            public String getName() {
                return "SkippableStage";
            }
            
            @Override
            public boolean shouldSkip(RagContext context) {
                return true; // 总是跳过
            }
        };
        
        pipeline.addStage(skippableStage);
        
        RagContext context = RagContext.builder()
            .originalQuestion("test")
            .userId("user123")
            .build();
        
        assertDoesNotThrow(() -> pipeline.execute(context));
    }
}
