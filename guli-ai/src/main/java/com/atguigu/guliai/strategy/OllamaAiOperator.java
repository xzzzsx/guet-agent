package com.atguigu.guliai.strategy;

import com.atguigu.common.utils.StringUtils;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.vo.QueryVo;
import com.atguigu.system.domain.ChatKnowledge;
import com.atguigu.guliai.etl.OllamaKnowledgeEtlService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@Component
@AiBean(SystemConstant.MODEL_TYPE_OLLAMA)
public class OllamaAiOperator implements AiOperator {
    private static final Logger log = LoggerFactory.getLogger(OllamaAiOperator.class);
    private static final String METADATA_CHUNK_SIZE = "chunkSize";
    private static final String METADATA_KNOWLEDGE_ID = "knowledgeId";
    private static final int CHUNK_SIZE = 500;
    private static final double SIMILARITY_THRESHOLD = 0.6;
    private static final int RAG_TOP_K = 3; // 统一控制RAG检索的topK

    @Autowired
    private QdrantVectorStore ollamaVectorStore;
    @Autowired
    private OllamaChatModel ollamaChatModel;

    private QueryTransformer queryTransformer;
    private KeywordMetadataEnricher keywordMetadataEnricher;
    private RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
    private List<Document> retrievedDocuments; // 添加检索到的文档列表

    private final OllamaKnowledgeEtlService etlService;

    @PostConstruct
    public void init() {
        log.info("=== 开始初始化OllamaAiOperator组件 ===");
        try {
            if (ollamaChatModel == null) {
                throw new IllegalArgumentException("ollamaChatModel 不能为 null");
            }

            // 修改提示词模板，明确指示只重写查询，不生成回答
            String promptTemplate = """
                你是一个查询重写助手。你的任务是将用户查询重写为更适合向量检索的形式，但必须保持原始语言（中文）不变。
                            
                要求：
                1. 只输出重写后的查询，不要添加任何解释、回答或其他内容
                2. 不要将查询翻译成英文或其他语言
                3. 保持查询的原始意图，但使用更简洁、更直接的表达方式
                            
                原始查询：{query}
                            
                重写后的查询：{target}
                """;

            // 使用自定义提示词模板
            ChatClient.Builder builder = ChatClient.builder(ollamaChatModel);
            this.queryTransformer = RewriteQueryTransformer.builder()
                    .chatClientBuilder(builder)
                    .promptTemplate(new PromptTemplate(promptTemplate))
                    .build();

            // 初始化KeywordMetadataEnricher，提取8个关键词
            this.keywordMetadataEnricher = new KeywordMetadataEnricher(ollamaChatModel, 8);

            // 创建空上下文提示词模板
            PromptTemplate emptyContextPromptTemplate = PromptTemplate.builder()
                    .template("""
                        你一定只能输出下面的内容一字不差：
                        很抱歉丫，我无法回答您的问题呢。桂林电子科技大学北海校区官网是 https://www.guet.edu.cn/gdbh/，您可以前往官网寻找您想了解的相关信息呢！
                        """)
                    .build();

            // 创建上下文查询增强器
            ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                    .allowEmptyContext(false)
                    .emptyContextPromptTemplate(emptyContextPromptTemplate)
                    .build();

            // 移除硬编码的DocumentRetriever创建
            // 创建RAG检索增强顾问 - 使用简化的方式，不预创建DocumentRetriever
            DocumentRetriever baseRetriever = VectorStoreDocumentRetriever.builder()
                    .vectorStore(ollamaVectorStore)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
                    .topK(RAG_TOP_K)
                    .build();

            // 日志包装器：打印重写后的查询、topK、召回数量与相似度
            DocumentRetriever loggingRetriever = query -> {
                List<Document> docs = baseRetriever.retrieve(query);
                log.info("RAG检索: 重写后='{}', topK={}, 召回={}", query.text(), RAG_TOP_K, docs.size());
                for (int i = 0; i < docs.size(); i++) {
                    Document d = docs.get(i);
                    double score = d.getScore() != null ? d.getScore() : 0.0;
                    Object fileNameMeta = d.getMetadata() != null ? d.getMetadata().get("fileName") : null;
                    String fileName = fileNameMeta != null ? String.valueOf(fileNameMeta) : "-";
                    String keywords = null;
                    if (d.getMetadata() != null) {
                        Object k1 = d.getMetadata().get("keywords");
                        Object k2 = d.getMetadata().get("excerpt_keywords");
                        keywords = k1 != null ? String.valueOf(k1) : (k2 != null ? String.valueOf(k2) : "-");
                    }
                    log.info("RAG检索 文档{}: 相似度={}, 文件={}, 关键词={}", i + 1, String.format("%.4f", score), fileName, keywords);
                }
                return docs;
            };

            this.retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(loggingRetriever)
                    .queryAugmenter(queryAugmenter)
                    .queryTransformers(this.queryTransformer)
                    .build();

            // 初始化检索到的文档列表
            this.retrievedDocuments = new ArrayList<>();
            log.info("组件初始化成功");

        } catch (Exception e) {
            log.error("组件初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
    }

    @Autowired
    public OllamaAiOperator(OllamaKnowledgeEtlService etlService) {
        this.etlService = etlService;
    }

    @Override
    public void addDocs(ChatKnowledge chatKnowledge) {
        try {
            log.info("=== 开始处理文档: {} (项目ID: {}) ===",
                    chatKnowledge.getFileName(), chatKnowledge.getProjectId());
            if (StringUtils.isEmpty(chatKnowledge.getContent())) {
                log.error("文档内容为空，跳过处理");
                return;
            }
            // 使用标准ETL流程：Extract -> Transform -> Load
            this.etlService.etlIngest(chatKnowledge);
        } catch (Exception e) {
            log.error("文档处理失败", e);
        }
    }

    @Override
    public Flux<String> chat_stream(Message[] messages) {
        try {
            // 获取用户查询
            String userQuery = "";
            List<Message> userMessages = Arrays.stream(messages)
                    .filter(m -> m.getMessageType() == MessageType.USER)
                    .collect(Collectors.toList());
            if (!userMessages.isEmpty()) {
                userQuery = userMessages.get(userMessages.size() - 1).getText();
            }

            // 创建系统消息，明确指示回答范围
            SystemMessage systemMessage = new SystemMessage("""
            你是一个严格基于桂林电子科技大学北海校区知识库内容回答问题的AI助手。
            
            规则：
            1. 只回答与桂林电子科技大学北海校区直接相关的问题
            2. 如果检索到的文档与用户问题关联性不强，礼貌的拒绝回答
            3. 不要基于你的通用知识回答问题，只能基于已有知识库内容回答，不能胡编乱造或者篡改知识库
            """);

            List<Message> messageList = new ArrayList<>();
            messageList.add(systemMessage);
            if (!userMessages.isEmpty()) {
                messageList.add(userMessages.get(userMessages.size() - 1));
            }

            // 使用RetrievalAugmentationAdvisor处理聊天
            ChatClient chatClient = ChatClient.builder(ollamaChatModel)
                    .defaultAdvisors(retrievalAugmentationAdvisor)
                    .defaultAdvisors(new SimpleLoggerAdvisor())
                    .build();

            return chatClient.prompt()
                    .messages(messageList)  // 包含系统消息和用户消息
                    .stream()
                    .content()
                    .doOnError(e -> log.error("聊天流处理错误", e));
        } catch (Exception e) {
            log.error("构建聊天流失败", e);
            return Flux.error(e);
        }
    }
}