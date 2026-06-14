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

            // todo: 清除字符串中的非法代理项（unpaired surrogates）以及不可见的控制字符。
            //     * 保留合法的 Unicode 字符（包括完整代理对表示的 Emoji）
            List<Document> docs = getLoader(type).load(file);
            List<Document> chunks = getSplitter(type).split(docs);
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
