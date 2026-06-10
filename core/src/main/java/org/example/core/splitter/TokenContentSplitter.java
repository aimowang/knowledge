package org.example.core.splitter;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TokenContentSplitter extends TokenTextSplitter implements ContentSplitter {
    @Override
    public List<String> support() {
        return List.of("md");
    }
}
