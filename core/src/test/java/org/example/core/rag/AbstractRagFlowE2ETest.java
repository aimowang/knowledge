package org.example.core.rag;

import org.example.core.rag.context.RagContext;
import org.example.core.rag.orchestrator.DefaultRagOrchestrator;
import org.example.core.rag.orchestrator.RagOrchestrator;
import org.example.core.rag.pipeline.DefaultRagPipeline;
import org.example.core.rag.pipeline.RagPipeline;
import org.example.core.rag.pipeline.stage.QueryPreprocessingStage;
import org.example.model.RagAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AbstractRagFlow 端到端测试
 */
class AbstractRagFlowE2ETest {
    
    @Mock
    private DefaultRagPipeline pipeline;
    
    @Mock
    private DefaultRagOrchestrator orchestrator;
    
    private TestRagFlow ragFlow;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 模拟管道执行
        when(pipeline.execute(any(RagContext.class))).thenAnswer(invocation -> {
            RagContext context = invocation.getArgument(0);
            return new RagAnswer("测试答案", java.util.List.of("source1"));
        });
        
        ragFlow = new TestRagFlow(pipeline, orchestrator);
    }
    
    @Test
    void testExecuteRagSuccess() {
        RagAnswer answer = ragFlow.executeRag("Spring Boot 是什么？", "user123", "docs");
        
        assertNotNull(answer);
        assertEquals("测试答案", answer.getAnswer());
        assertEquals(1, answer.getSources().size());
        
        // 验证编排器被调用
        verify(orchestrator).beforeExecute(any(RagContext.class));
        verify(orchestrator).afterExecute(any(RagContext.class), any(RagAnswer.class));
        
        // 验证管道被执行
        verify(pipeline).execute(any(RagContext.class));
    }
    
    @Test
    void testCacheHit() {
        // 模拟缓存命中
        doAnswer(invocation -> {
            RagContext context = invocation.getArgument(0);
            context.addMetadata("cacheHit", true);
            context.setAnswer("缓存答案");
            context.setSources(java.util.List.of("cached_source"));
            return null;
        }).when(orchestrator).beforeExecute(any(RagContext.class));
        
        RagAnswer answer = ragFlow.executeRag("问题", "user123", "docs");
        
        assertNotNull(answer);
        assertEquals("缓存答案", answer.getAnswer());
        
        // 缓存命中时不应该执行管道
        verify(pipeline, never()).execute(any(RagContext.class));
    }
    
    @Test
    void testCreateContext() {
        RagContext context = ragFlow.testCreateContext("问题", "user123", "docs");
        
        assertNotNull(context);
        assertEquals("问题", context.getOriginalQuestion());
        assertEquals("user123", context.getUserId());
        assertEquals("docs", context.getSource());
    }
    
    /**
     * 测试用的 RAG 流程实现
     */
    private static class TestRagFlow extends AbstractRagFlow {
        
        public TestRagFlow(DefaultRagPipeline pipeline, DefaultRagOrchestrator orchestrator) {
            super(pipeline, orchestrator);
        }
        
        protected void configurePipeline(DefaultRagPipeline pipeline) {
            // 测试配置
        }
        
        protected void configureOrchestrator(DefaultRagOrchestrator orchestrator) {
            // 测试配置
        }
        
        public RagContext testCreateContext(String question, String userId, String source) {
            return createContext(question, userId, source);
        }
        
        @Override
        public java.util.List<String> support() {
            return java.util.List.of("test");
        }

        @Override
        protected void configurePipeline(RagPipeline pipeline) {

        }

        @Override
        protected void configureOrchestrator(RagOrchestrator orchestrator) {

        }
    }
}
