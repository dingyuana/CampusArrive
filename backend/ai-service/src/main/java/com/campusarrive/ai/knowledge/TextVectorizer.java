package com.campusarrive.ai.knowledge;

import java.util.HashSet;
import java.util.Set;

/**
 * 文本向量化（测试与降级检索用）。
 *
 * <p>规格来源：AID 5.4 — 真实环境由 MaxKB 内置向量化模型生成 1024 维 embedding，
 * 存入 PostgreSQL+pgvector 并以 HNSW 索引支撑语义相似度召回。</p>
 *
 * <p>此处用字符级 bigram 集合 + Jaccard 系数模拟语义相似度，取值 ∈ [0,1]，
 * 供单元测试与 FAQ 降级检索（AID 9.2）复用，不依赖外部模型与向量库，
 * 使测试可在无 MaxKB/pgvector 环境下独立运行。</p>
 */
public final class TextVectorizer {

    private TextVectorizer() {
    }

    /**
     * 提取中英文混合文本的字符 bigram 集合。
     *
     * <p>先转小写并去除空白，再按相邻两字符切分。
     * bigram 对中文（如"食堂"→["食堂"]）与英文（如"library"→["li","ib",...]）均适用，
     * 兼顾中文语义片段与英文词形。</p>
     */
    public static Set<String> bigrams(String text) {
        Set<String> set = new HashSet<>();
        if (text == null || text.length() < 2) {
            return set;
        }
        String normalized = text.toLowerCase().replaceAll("\\s+", "");
        for (int i = 0; i < normalized.length() - 1; i++) {
            set.add(normalized.substring(i, i + 2));
        }
        return set;
    }

    /**
     * 计算两段文本的重叠系数（overlap coefficient），模拟向量余弦相似度 ∈ [0,1]。
     *
     * <p>两文本 bigram 集合的交集大小除以较小集合大小；任一为空返回 0。
     * 当查询片段全部命中文档时返回 1.0，符合"查询被文档覆盖"的检索直觉。</p>
     */
    public static double similarity(String a, String b) {
        Set<String> sa = bigrams(a);
        Set<String> sb = bigrams(b);
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(sa);
        intersection.retainAll(sb);
        int min = Math.min(sa.size(), sb.size());
        return min == 0 ? 0.0 : (double) intersection.size() / min;
    }
}
