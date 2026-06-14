package org.example.core.splitter;

import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归字符文本分割器
 * <p>
 * 按分隔符优先级递归切分：段落 → 行 → 句 → 字符。
 * 切分后通过合并相邻小片段确保每个分块在 <b>200~500 字符</b> 之间。
 * <p>
 * 最小尺寸为尽力保证（末尾剩余片段可能偏小），最大尺寸为硬限制。
 */
@Component
public class RecursiveCharacterTextSplitter extends TextSplitter implements ContentSplitter {

    /** 目标最小分块大小（字符数） */
    private static final int MIN_CHUNK_SIZE = 200;

    /** 最大分块大小（字符数），超过此值的片段会递归切分 */
    private static final int MAX_CHUNK_SIZE = 500;

    /**
     * 分隔符优先级：从粗到细，依次尝试
     */
    private static final List<String> SEPARATORS = List.of("\n## ", "\n### ", "\n", "。", "！", "？", " ", "");

    @Override
    protected List<String> splitText(String text) {
        // 1. 递归切分（保持现有逻辑）
        List<String> chunks = splitTextRecursive(text, 0);

        // 2. 合并小片段，保证尺寸不低于 MIN_CHUNK_SIZE
        return mergeSmallChunks(chunks);
    }

    // ==================== 递归切分 ====================

    private List<String> splitTextRecursive(String text, int separatorIndex) {
        if (separatorIndex >= SEPARATORS.size()) {
            return List.of(text);
        }

        String separator = SEPARATORS.get(separatorIndex);
        List<String> result = new ArrayList<>();

        String[] parts = text.split(separator, -1);
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            // 超过上限 → 用更细的分隔符递归切分
            if (trimmed.length() > MAX_CHUNK_SIZE && separatorIndex < SEPARATORS.size() - 1) {
                result.addAll(splitTextRecursive(trimmed, separatorIndex + 1));
            } else {
                result.add(trimmed);
            }
        }
        return result;
    }

    // ==================== 合并小片段 ====================

    /**
     * 将小于 {@link #MIN_CHUNK_SIZE} 的相邻片段向前合并，
     * 使得最终分块尽可能落在 [MIN_CHUNK_SIZE, MAX_CHUNK_SIZE] 区间内。
     */
    private List<String> mergeSmallChunks(List<String> chunks) {
        if (chunks.size() <= 1) return chunks;

        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder(chunks.get(0));

        for (int i = 1; i < chunks.size(); i++) {
            String chunk = chunks.get(i);

            if (buffer.length() >= MIN_CHUNK_SIZE) {
                // 缓冲区已达目标大小 → 落盘，开始新片段
                result.add(buffer.toString());
                buffer = new StringBuilder(chunk);
            } else if (buffer.length() + chunk.length() <= MAX_CHUNK_SIZE) {
                // 合并后仍不超上限 → 合并以提升到最小尺寸
                buffer.append(chunk);
            } else {
                // 合并会超上限 → 当前缓冲区落盘，chunk 成为新缓冲区
                result.add(buffer.toString());
                buffer = new StringBuilder(chunk);
            }
        }

        // 处理最后一段缓冲区
        if (!buffer.isEmpty()) {
            // 末尾小片段尝试与上一个结果合并（仅当不超限时）
            if (buffer.length() < MIN_CHUNK_SIZE && !result.isEmpty()
                    && result.get(result.size() - 1).length() + buffer.length() <= MAX_CHUNK_SIZE) {
                String last = result.remove(result.size() - 1);
                result.add(last + buffer);
            } else {
                result.add(buffer.toString());
            }
        }

        return result;
    }

    @Override
    public List<String> support() {
        return List.of("pdf", "doc", "docs");
    }
}
