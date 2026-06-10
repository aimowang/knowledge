package org.example.core.util;

public class CosUtil {
    /**
     * 计算两个向量的余弦相似度
     * @param a 向量 a
     * @param b 向量 b
     * @return 余弦相似度值（-1 到 1 之间）
     */
    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0;
        }

        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }

        // 避免除零错误
        if (na == 0 || nb == 0) {
            return 0;
        }

        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
