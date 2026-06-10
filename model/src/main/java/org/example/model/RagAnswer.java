package org.example.model;

import lombok.Data;

import java.util.List;

/**
 * RAG 答案结果（包含来源信息）
 */
@Data
public class RagAnswer {
    
    /**
     * 生成的答案内容
     */
    private String answer;
    
    /**
     * 参考的文档列表（来源）
     */
    private List<String> sources;
    
    /**
     * 问题分类
     */
    private String category;
    
    /**
     * 复杂度级别
     */
    private String complexity;
    
    public RagAnswer() {
    }
    
    public RagAnswer(String answer, List<String> sources) {
        this.answer = answer;
        this.sources = sources;
    }
    
    /**
     * 格式化为带引用的文本
     * 例如：答案是...[1][2]
     */
    public String formatWithCitations() {
        if (sources == null || sources.isEmpty()) {
            return answer;
        }
        
        StringBuilder sb = new StringBuilder(answer);
        sb.append("\n\n---\n**参考资料：**\n");
        
        for (int i = 0; i < sources.size(); i++) {
            sb.append(String.format("\n   来源: %s\n", sources.get(i)));
        }
        
        return sb.toString();
    }
}
