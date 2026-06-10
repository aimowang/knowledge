package org.example.core.splitter;

import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecursiveCharacterTextSplitter extends TextSplitter implements ContentSplitter {

    @Override
    protected List<String> splitText(String text) {
        return List.of("\n## ", "\n### ", "\n", " ", "");
    }

    @Override
    public List<String> support() {
        return List.of("pdf", "doc", "docs");
    }
}
