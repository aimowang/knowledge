package org.example.core.splitter;

import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RecursiveCharacterTextSplitter extends TextSplitter implements ContentSplitter {

    /**
     * 按分隔符优先级递归切分：段落 → 行 → 句 → 字符
     */
    private static final List<String> SEPARATORS = List.of("\n## ", "\n### ", "\n", "。", "！", "？", "！", "？", " ", "");

    @Override
    protected List<String> splitText(String text) {
        return splitTextRecursive(text, 0);
    }

    private List<String> splitTextRecursive(String text, int separatorIndex) {
        if (separatorIndex >= SEPARATORS.size()) {
            return List.of(text);
        }

        String separator = SEPARATORS.get(separatorIndex);
        List<String> result = new ArrayList<>();

        // 按当前分隔符切分
        String[] parts = text.split(separator, -1);
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            // 如果子段仍然太长，递归使用下一个分隔符
            if (trimmed.length() > 500 && separatorIndex < SEPARATORS.size() - 1) {
                result.addAll(splitTextRecursive(trimmed, separatorIndex + 1));
            } else {
                result.add(trimmed);
            }
        }
        return result;
    }

    @Override
    public List<String> support() {
        return List.of("pdf", "doc", "docs");
    }
}
