package org.example.core.service;

import jakarta.annotation.Resource;
import org.example.core.document.DocumentLoader;
import org.example.core.splitter.ContentSplitter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class KnowledgeEmbeddingService {
    @Resource
    private List<DocumentLoader> loaders;
    @Resource
    private List<ContentSplitter> splitters;
    @Resource
    private VectorStore vectorStore;


    public void embedding(List<File> files) {
        for (File file : files) {
            String[] split = file.getName().split("\\.");
            String type = split[split.length - 1];
            List<Document> docs = getLoader(type).load(file);
            List<Document> chunks = getSplitter(type).split(docs);
            vectorStore.add(chunks);
        }
    }

    private DocumentLoader getLoader(String type) {
        for (DocumentLoader loader : loaders) {
            if (loader.support().contains(type)) {
                return loader;
            }
        }
        return loaders.get(0);
    }

    private ContentSplitter getSplitter(String type) {
        for (ContentSplitter splitter : splitters) {
            if (splitter.support().contains(type)) {
                return splitter;
            }
        }
        return splitters.get(0);
    }
}
