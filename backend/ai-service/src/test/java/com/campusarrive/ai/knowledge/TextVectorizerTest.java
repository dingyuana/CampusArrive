package com.campusarrive.ai.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TextVectorizer} 单元测试。
 *
 * <p>验证字符 bigram 提取与 Jaccard 相似度计算的边界行为，
 * 支撑混合检索的向量相似度分量（AID 5.4）。</p>
 */
@DisplayName("UT-AI: 文本向量化")
class TextVectorizerTest {

    @Test
    @DisplayName("相同文本相似度为 1.0")
    void identicalTextSimilarityOne() {
        assertEquals(1.0, TextVectorizer.similarity("食堂在哪里", "食堂在哪里"));
    }

    @Test
    @DisplayName("无交集文本相似度为 0.0")
    void disjointTextSimilarityZero() {
        assertEquals(0.0, TextVectorizer.similarity("abc", "xyz"));
    }

    @Test
    @DisplayName("部分重叠相似度介于 0 与 1 之间")
    void partialOverlapBetweenZeroAndOne() {
        // "食堂在哪里" 与 "食堂几点开门" 共享 bigram "食堂"，部分重叠
        double s = TextVectorizer.similarity("食堂在哪里", "食堂几点开门");
        assertTrue(s > 0.0 && s < 1.0, "相似度应介于 0 与 1 之间: " + s);
    }

    @Test
    @DisplayName("空或过短文本相似度为 0")
    void emptyOrShortTextSimilarityZero() {
        assertEquals(0.0, TextVectorizer.similarity("", "abc"));
        assertEquals(0.0, TextVectorizer.similarity("a", "abc"));
        assertEquals(0.0, TextVectorizer.similarity("abc", ""));
    }

    @Test
    @DisplayName("bigram 集合大小正确")
    void bigramsSize() {
        assertEquals(0, TextVectorizer.bigrams("a").size(), "单字符无 bigram");
        assertEquals(2, TextVectorizer.bigrams("abc").size(), "3 字符产生 2 个 bigram");
        // 4 字符产生 3 个 bigram：食堂、堂在、在哪
        assertEquals(3, TextVectorizer.bigrams("食堂在哪").size(), "4 字符产生 3 个 bigram");
    }

    @Test
    @DisplayName("大小写与空白归一化")
    void normalizationCaseAndWhitespace() {
        // "Lib Rary" 归一化为 "library"，与 "library" 相同
        assertEquals(1.0, TextVectorizer.similarity("Lib Rary", "library"));
    }
}
