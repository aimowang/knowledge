package org.example.core.compress;

import org.example.core.splitter.SentencesSplitter;
import org.example.core.util.CosUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class EmbeddingCompressor implements DocumentCompressor {

    private final EmbeddingModel embeddingModel;
    private final SentencesSplitter sentencesSplitter;
    private final double threshold;      // 相似度阈值，例如 0.6
    private final int minSentences;

    // 最少保留句子数（防止全部被过滤）

    public EmbeddingCompressor(EmbeddingModel embeddingModel,
                               SentencesSplitter sentencesSplitter,
                               @Value("${compressor.embedding.threshold:0.6}") double threshold,
                               @Value("${compressor.embedding.min-sentences:2}") int minSentences) {
        this.embeddingModel = embeddingModel;
        this.sentencesSplitter = sentencesSplitter;
        this.threshold = threshold;
        this.minSentences = minSentences;
    }

    @Override
    public List<Document> compress(List<Document> documents, String query) {
        float[] queryEmbedding = embeddingModel.embed(query);

        return documents.stream().map(doc -> {
            List<String> sentences = sentencesSplitter.splitText(doc.getFormattedContent());
            if (sentences.size() <= minSentences) {
                return doc;  // 太短，不压缩
            }

            // 计算每个句子的相似度，并过滤
            List<String> keptSentences = new ArrayList<>();
            for (String sentence : sentences) {
                float[] sentEmbedding = embeddingModel.embed(sentence);
                double sim = CosUtil.cosine(queryEmbedding, sentEmbedding);
                if (sim >= threshold) {
                    keptSentences.add(sentence);
                }
            }
            // 如果过滤后太少，回退到保留原始内容
            if (keptSentences.size() < minSentences) {
                return doc;
            }
            // 重新拼接成压缩后的文档
            String compressedContent = String.join(" ", keptSentences);
            return new Document(compressedContent, doc.getMetadata());
        }).collect(Collectors.toList());
    }
}
