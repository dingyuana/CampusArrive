package com.campusarrive.ai.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BM25 关键词评分（测试与降级检索用）。
 *
 * <p>规格来源：AID 5.4 — 混合检索 = 向量相似度检索（权重 0.7）+ 关键词 BM25 检索（权重 0.3），
 * 提升召回率。</p>
 *
 * <p>此处实现标准 BM25（k1=1.5, b=0.75），分词采用"中文按字 + 英文按词"的简单策略，
 * 供单元测试与降级检索复用。真实环境由 MaxKB 内置 BM25 引擎完成。</p>
 */
public final class Bm25Scorer {

    /** BM25 饱和参数。 */
    public static final double K1 = 1.5;
    /** BM25 长度归一化参数。 */
    public static final double B = 0.75;

    private Bm25Scorer() {
    }

    /**
     * 对候选分块按 query 计算 BM25 分数，返回按分数降序排列的结果。
     *
     * @param query   用户查询
     * @param chunks  候选分块集合
     * @return 按 BM25 分数降序排列的分块列表；query 或候选为空时返回空列表
     */
    public static List<ScoredChunk> score(String query, List<KnowledgeChunk> chunks) {
        List<String> terms = tokenize(query);
        if (terms.isEmpty() || chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        double avgDl = chunks.stream()
                .mapToInt(c -> tokenize(c.content()).size())
                .average().orElse(1.0);
        if (avgDl <= 0) {
            avgDl = 1.0;
        }
        // 文档频率 df
        Map<String, Integer> df = new HashMap<>();
        for (KnowledgeChunk c : chunks) {
            Set<String> termSet = new HashSet<>(tokenize(c.searchableText()));
            for (String t : termSet) {
                df.merge(t, 1, Integer::sum);
            }
        }
        List<ScoredChunk> scored = new ArrayList<>();
        for (KnowledgeChunk c : chunks) {
            List<String> cTerms = tokenize(c.searchableText());
            Map<String, Long> tf = cTerms.stream()
                    .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
            double score = 0.0;
            double dl = cTerms.size();
            for (String t : terms) {
                Integer n = df.get(t);
                if (n == null) {
                    continue;
                }
                Long f = tf.get(t);
                if (f == null || f == 0) {
                    continue;
                }
                double idf = Math.log(1.0 + (chunks.size() - n + 0.5) / (n + 0.5));
                double denom = f + K1 * (1 - B + B * dl / avgDl);
                score += idf * (f * (K1 + 1)) / denom;
            }
            scored.add(new ScoredChunk(c, score));
        }
        scored.sort((x, y) -> Double.compare(y.score(), x.score()));
        return scored;
    }

    /**
     * 简单中英文混合分词：中文按单字、英文/数字按连续字符序列。
     *
     * <p>标点与空白作为分隔被丢弃。例如 "食堂在哪里" → ["食","堂","在","哪","里"]，
     * "Library 8:00" → ["library","8","00"]。</p>
     */
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String lower = text.toLowerCase();
        StringBuilder eng = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (isAsciiAlnum(ch)) {
                eng.append(ch);
            } else {
                if (eng.length() > 0) {
                    tokens.add(eng.toString());
                    eng.setLength(0);
                }
                if (Character.isLetterOrDigit(ch)) {
                    tokens.add(String.valueOf(ch));
                }
            }
        }
        if (eng.length() > 0) {
            tokens.add(eng.toString());
        }
        return tokens;
    }

    private static boolean isAsciiAlnum(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
    }

    /** 带分数的分块结果。 */
    public record ScoredChunk(KnowledgeChunk chunk, double score) {
    }
}
