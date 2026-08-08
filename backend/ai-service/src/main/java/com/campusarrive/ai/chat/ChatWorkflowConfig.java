package com.campusarrive.ai.chat;

import com.campusarrive.ai.chat.workflow.ChatWorkflow;
import com.campusarrive.ai.chat.workflow.DeepSeekGenerator;
import com.campusarrive.ai.chat.workflow.FallbackFaqMatcher;
import com.campusarrive.ai.chat.workflow.KeywordSafetyFilter;
import com.campusarrive.ai.chat.workflow.LocalDeepSeekGenerator;
import com.campusarrive.ai.chat.workflow.PiiMasker;
import com.campusarrive.ai.chat.workflow.SafetyFilterNode;
import com.campusarrive.ai.knowledge.HybridRetrievalStrategy;
import com.campusarrive.ai.knowledge.InMemoryKnowledgeStore;
import com.campusarrive.ai.knowledge.KnowledgeRetrievalService;
import com.campusarrive.ai.knowledge.SeedKnowledgeBase;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 对话工作流配置。
 *
 * <p>装配 5 节点工作流所需 Bean:安全过滤、知识检索、PII 脱敏、DeepSeek 生成、FAQ 降级,
 * 以及会话上下文管理与限流器。种子知识库在启动时自动加载。</p>
 *
 * <p>真实 MaxKB/DeepSeek HTTP 客户端在 INFRA-1.3 环境就绪后替换本地实现,
 * 通过 {@code @ConditionalOnProperty} 切换,此处先提供内存实现保证可联调。</p>
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
public class ChatWorkflowConfig {

    /** 知识库内存存储(启动时加载种子数据)。 */
    @Bean
    public InMemoryKnowledgeStore knowledgeStore() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        SeedKnowledgeBase.loadAll(store);
        return store;
    }

    /** 知识检索服务(混合检索)。 */
    @Bean
    public KnowledgeRetrievalService knowledgeRetrievalService(InMemoryKnowledgeStore store) {
        return new HybridRetrievalStrategy(store);
    }

    /** 节点 1:安全过滤。 */
    @Bean
    public SafetyFilterNode safetyFilter() {
        return new KeywordSafetyFilter();
    }

    /** 节点 3:PII 脱敏。 */
    @Bean
    public PiiMasker piiMasker() {
        return new PiiMasker();
    }

    /** 节点 4:DeepSeek 生成(本地占位实现)。 */
    @Bean
    public LocalDeepSeekGenerator deepSeekGenerator() {
        return new LocalDeepSeekGenerator();
    }

    /** FAQ 降级匹配器。 */
    @Bean
    public FallbackFaqMatcher fallbackMatcher(KnowledgeRetrievalService retrieval) {
        return new FallbackFaqMatcher(retrieval);
    }

    /** 对话工作流编排器。 */
    @Bean
    public ChatWorkflow chatWorkflow(SafetyFilterNode safetyFilter,
                                     KnowledgeRetrievalService retrieval,
                                     PiiMasker piiMasker,
                                     LocalDeepSeekGenerator generator,
                                     FallbackFaqMatcher fallbackMatcher) {
        return new ChatWorkflow(safetyFilter, retrieval, piiMasker, generator, fallbackMatcher);
    }

    /** 会话上下文管理器。 */
    @Bean
    public ConversationContextManager contextManager() {
        return new ConversationContextManager();
    }

    /** 限流器(10 次/分钟)。 */
    @Bean
    public ChatRateLimiter rateLimiter() {
        return new ChatRateLimiter(10, 60);
    }
}
