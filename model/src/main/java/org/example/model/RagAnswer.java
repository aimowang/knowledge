package org.example.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 答案结果（包含来源信息）
 */
@Data
public class RagAnswer {

    private String answer;

    private List<String> sources;

    private String category;

    private String complexity;

    /** Agentic RAG 扩展元数据（trajectoryId, loopCount, agenticMode 等） */
    private Map<String, String> metadata = new LinkedHashMap<>();

    public RagAnswer() {
    }

    public RagAnswer(String answer, List<String> sources) {
        this.answer = answer;
        this.sources = sources;
    }

    /**
     * 格式化为带引用的文本
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
