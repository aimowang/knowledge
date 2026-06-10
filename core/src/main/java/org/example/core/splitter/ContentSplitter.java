package org.example.core.splitter;

import org.example.core.document.State;
import org.springframework.ai.document.Document;

import java.util.List;

public interface ContentSplitter extends State {
    List<Document> split(List<Document> documents);
}
