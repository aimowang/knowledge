package org.example.core.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.core.document.DocumentLoader;
import org.example.core.retrieval.Bm25Indexer;
import org.example.core.splitter.ContentSplitter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class KnowledgeEmbeddingService {
    @Resource
    private List<DocumentLoader> loaders;
    @Resource
    private List<ContentSplitter> splitters;
    @Resource
    private VectorStore vectorStore;

    @Resource
    private Bm25Indexer bm25Indexer;


    @Transactional(rollbackFor = Exception.class)
    public void embedding(List<File> files) {
        for (File file : files) {
            String[] split = file.getName().split("\\.");
            String type = split[split.length - 1];
            log.info("开始处理文件: {}, 类型: {}", file.getName(), type);

            // 清除文档中的非法代理项（unpaired surrogates），否则 Milvus gRPC 会报错
            List<Document> docs = sanitizeDocuments(getLoader(type).load(file));
            List<Document> chunks = sanitizeDocuments(getSplitter(type).split(docs));
            if (chunks.isEmpty()) {
                log.warn("文件 {} 未产生有效分块", file.getName());
                continue;
            }
            // 分批提交（API 限制每批最多 10 条）
            int batchSize = 8;
            for (int i = 0; i < chunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, chunks.size());
                List<Document> batch = chunks.subList(i, end);
                vectorStore.add(batch);
                bm25Indexer.addDocuments(batch);
                log.info("文件 {} 批次 {}/{} 提交完成, {} 条 (BM25 索引: {})", file.getName(), end, chunks.size(), batch.size(), bm25Indexer.size());
            }
            log.info("文件 {} 处理完成, 共 {} 个分块", file.getName(), chunks.size());
        }
    }

    /**
     * 清除文档内容中的非法代理项（unpaired surrogates），
     * 保留合法 Unicode 字符（包括完整代理对表示的 Emoji）。
     */
    private List<Document> sanitizeDocuments(List<Document> documents) {
        List<Document> result = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            String clean = sanitizeText(doc.getText());
            // 创建新 Document（Spring AI Document 不可变，无 setText）
            Document sanitized = new Document(clean, new java.util.HashMap<>(doc.getMetadata()));
            result.add(sanitized);
        }
        return result;
    }

    /**
     * 清除字符串中的非法代理项，保留合法字符。
     */
    private String sanitizeText(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    sb.append(c);          // 完整代理对，保留
                    sb.append(text.charAt(++i));
                } else {
                    sb.append('�');   // 孤高代理 → 替换字符
                }
            } else if (Character.isLowSurrogate(c)) {
                sb.append('�');       // 孤低代理 → 替换字符
            } else {
                sb.append(c);              // 正常字符，保留
            }
        }
        return sb.toString();
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
