package org.example.core.splitter;

import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SentencesSplitter extends TextSplitter implements ContentSplitter {
    @Override
    public List<String> splitText(String text) {
        return List.of("。", "！", "？", "\\.", "\\!", "\\?");
    }
}
