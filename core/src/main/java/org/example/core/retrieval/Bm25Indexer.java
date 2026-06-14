package org.example.core.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 轻量级 BM25 搜索索引器
 * <p>
 * 基于经典 BM25 算法实现关键词稀疏检索，支持中英文混合文本。
 * <p>
 * BM25 评分公式:
 * BM25(q, d) = Σ [ IDF(t) * TF(t,d) * (k₁+1) / (TF(t,d) + k₁ * (1-b + b*|d|/avgdl)) ]
 * <p>
 * 中文采用单字切分，英文采用空格/标点分割 + 小写归一化。
 */
@Slf4j
@Component
public class Bm25Indexer {

    /** BM25 参数: 控制词频饱和度 */
    private static final double K1 = 1.2;

    /** BM25 参数: 控制文档长度归一化 (0 = 纯词频, 1 = 完全长度归一化) */
    private static final double B = 0.75;

    /** 倒排索引: term -> docKey -> 词频 */
    private final Map<String, Map<String, Integer>> invertedIndex = new ConcurrentHashMap<>();

    /** 文档长度: docKey -> 分词数 */
    private final Map<String, Integer> docLengths = new ConcurrentHashMap<>();

    /** 文档存储: docKey -> Document */
    private final Map<String, Document> docStore = new ConcurrentHashMap<>();

    /** 文档数 */
    private volatile int totalDocs = 0;

    /** 平均文档长度 */
    private volatile double avgDocLength = 0;

    /** 读写锁保证线程安全 */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 添加文档到 BM25 索引
     */
    public void addDocument(Document document) {
        if (document == null) return;
        String text = document.getText();
        if (text == null || text.isEmpty()) return;

        lock.writeLock().lock();
        try {
            String docKey = computeDocKey(text);
            List<String> tokens = tokenize(text);
            if (tokens.isEmpty()) return;

            // 更新文档存储
            docStore.put(docKey, document);
            docLengths.put(docKey, tokens.size());

            // 更新倒排索引
            Map<String, Integer> termFreq = new HashMap<>();
            for (String token : tokens) {
                termFreq.merge(token, 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
                invertedIndex.computeIfAbsent(entry.getKey(), k -> new ConcurrentHashMap<>())
                        .put(docKey, entry.getValue());
            }

            // 更新统计信息
            updateStats();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 批量添加文档
     */
    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) return;

        for (Document doc : documents) {
            addDocument(doc);
        }
        log.debug("BM25 索引更新完成，当前文档数: {}", totalDocs);
    }

    /**
     * 从索引中移除文档（通过内容匹配）
     */
    public void removeDocument(Document document) {
        if (document == null || document.getText() == null) return;

        lock.writeLock().lock();
        try {
            String docKey = computeDocKey(document.getText());
            removeByKey(docKey);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 通过文档 Key 移除
     */
    public void removeByKey(String docKey) {
        lock.writeLock().lock();
        try {
            docStore.remove(docKey);
            docLengths.remove(docKey);
            invertedIndex.values().forEach(idx -> idx.remove(docKey));
            invertedIndex.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            updateStats();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * BM25 搜索
     *
     * @param query 查询文本
     * @param topK  返回 topK 条结果
     * @return 按 BM25 得分降序排列的结果
     */
    public List<ScoredDocument> search(String query, int topK) {
        if (totalDocs == 0 || query == null || query.isEmpty()) {
            return List.of();
        }

        lock.readLock().lock();
        try {
            List<String> queryTokens = tokenize(query);
            if (queryTokens.isEmpty()) return List.of();

            // 计算每个文档的 BM25 得分
            Map<String, Double> scores = new HashMap<>();

            for (String term : queryTokens) {
                Map<String, Integer> postingList = invertedIndex.get(term);
                if (postingList == null) continue;

                double idf = computeIdf(term);
                for (Map.Entry<String, Integer> entry : postingList.entrySet()) {
                    String docKey = entry.getKey();
                    int tf = entry.getValue();
                    int docLength = docLengths.getOrDefault(docKey, 0);

                    double tfNorm = tf * (K1 + 1) / (tf + K1 * (1 - B + B * docLength / avgDocLength));
                    scores.merge(docKey, idf * tfNorm, Double::sum);
                }
            }

            // 排序并返回 topK
            return scores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(topK)
                    .map(entry -> new ScoredDocument(entry.getKey(), docStore.get(entry.getKey()), entry.getValue()))
                    .filter(sd -> sd.getDocument() != null)
                    .collect(Collectors.toList());

        } finally {
            lock.readLock().unlock();
        }
    }

    // ==================== BM25 核心算法 ====================

    /**
     * IDF 计算: ln((N - n_t + 0.5) / (n_t + 0.5) + 1)
     */
    private double computeIdf(String term) {
        Map<String, Integer> postingList = invertedIndex.get(term);
        int n = postingList == null ? 0 : postingList.size();
        return Math.log((totalDocs - n + 0.5) / (n + 0.5) + 1);
    }

    // ==================== 分词器 ====================

    /**
     * 分词（支持中英文混合）:
     * - 中文/CJK: 单字切分
     * - 英文/拉丁: 按空格和标点分割，转小写，过滤单字母
     * - 数字: 保留 (对版本号、型号等有用)
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) return List.of();

        List<String> tokens = new ArrayList<>();
        StringBuilder latinBuffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isCjkCharacter(c)) {
                flushLatinBuffer(latinBuffer, tokens);
                tokens.add(String.valueOf(c));
            } else if (Character.isLetter(c) || Character.isDigit(c) || c == '\'') {
                latinBuffer.append(c);
            } else {
                flushLatinBuffer(latinBuffer, tokens);
            }
        }
        flushLatinBuffer(latinBuffer, tokens);

        return tokens;
    }

    private void flushLatinBuffer(StringBuilder buffer, List<String> tokens) {
        if (buffer.length() == 0) return;
        String lower = buffer.toString().toLowerCase().trim();
        buffer.setLength(0);

        // 过滤过短的 token（减少噪声）
        if (lower.length() >= 2) {
            tokens.add(lower);
        }
    }

    /**
     * 判断是否为 CJK 字符（中日韩统一表意文字、假名、谚文）
     */
    private boolean isCjkCharacter(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO;
    }

    // ==================== 内部工具 ====================

    /**
     * 基于文档内容生成唯一 Key
     */
    private String computeDocKey(String content) {
        return Integer.toHexString(Objects.hash(content));
    }

    /**
     * 更新统计信息
     */
    private void updateStats() {
        totalDocs = docStore.size();
        avgDocLength = docLengths.values().stream()
                .mapToInt(Integer::intValue)
                .average().orElse(0);
    }

    /**
     * 清空索引
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            invertedIndex.clear();
            docLengths.clear();
            docStore.clear();
            totalDocs = 0;
            avgDocLength = 0;
            log.info("BM25 索引已清空");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 当前索引文档数 */
    public int size() {
        return totalDocs;
    }

    // ==================== 结果类 ====================

    /** BM25 搜索结果，包含文档引用和得分 */
    @lombok.Getter
    public static class ScoredDocument {
        private final String docKey;
        private final Document document;
        private final double score;

        public ScoredDocument(String docKey, Document document, double score) {
            this.docKey = docKey;
            this.document = document;
            this.score = score;
        }
    }
}
