package org.example.core.splitter;

import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class SentencesSplitter extends TextSplitter implements ContentSplitter {
    @Override
    public List<String> splitText(String text) {
        // 按中英文句号、感叹号、问号切分句子
        return Arrays.asList(text.split("(?<=[。！？.!?])\\s*"));
    }
}
