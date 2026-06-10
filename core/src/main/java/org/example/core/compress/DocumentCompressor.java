package org.example.core.compress;

import org.springframework.ai.document.Document;

import java.util.List;

public interface DocumentCompressor {
    List<Document> compress(List<Document> documents, String query);
}
