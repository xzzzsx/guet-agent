package com.atguigu.guliai.strategy;

import com.atguigu.common.utils.StringUtils;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.vo.QueryVo;
import com.atguigu.system.domain.ChatKnowledge;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
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
    // === 移动常量定义到类顶部 ===
    private static final String METADATA_CHUNK_SIZE = "chunkSize";
    private static final String METADATA_KNOWLEDGE_ID = "knowledgeId";
    private static final int CHUNK_SIZE = 500; // 分块大小常量

    @Autowired
    private QdrantVectorStore ollamaVectorStore;

    @Autowired
    private OllamaChatModel ollamaChatModel;

    private QueryRewriter queryRewriter;
    private PromptTemplate keywordPromptTemplate;

    @PostConstruct
    public void init() {
        log.info("=== 开始初始化OllamaAiOperator组件 ===");
        try {
            if (ollamaChatModel == null) {
                throw new IllegalArgumentException("ollamaChatModel 不能为 null");
            }

            this.keywordPromptTemplate = new PromptTemplate("""
                请从以下文本中提取3-5个最相关的关键词。
                要求:
                1. 只输出关键词，用空格分隔
                2. 不要解释或添加其他内容
                3. 关键词应具有代表性且能概括文本主题
                
                文本: {text}
                
                关键词:""");

            PromptTemplate rewriteTemplate = new PromptTemplate("""
                请将以下查询提取3-5个核心关键词，适配目标搜索系统: {target}
                要求:
                1. 保留原始查询的核心意图
                2. 去除冗余词和修饰词
                3. 用空格分隔关键词
                4. 不要解释或添加其他内容
                
                原始查询: {query}
                
                关键词:""");

            this.queryRewriter = new QueryRewriter(ollamaChatModel, rewriteTemplate);
            log.info("组件初始化成功");
        } catch (Exception e) {
            log.error("组件初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
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

            Document document = new Document(
                    chatKnowledge.getContent(),
                    Map.of(
                            "projectId", String.valueOf(chatKnowledge.getProjectId()),
                            "knowledgeId", String.valueOf(chatKnowledge.getKnowledgeId()),
                            "fileName", chatKnowledge.getFileName(),
                            "contentLength", String.valueOf(chatKnowledge.getContent().length()),
                            "uploadTime", new Date().toString(),
                            "documentType", "text"
                    )
            );

            // 严格按照图片中的构造方式
            TokenTextSplitter splitter = new TokenTextSplitter(CHUNK_SIZE, 100, 10, 5000, true);
            List<Document> splitDocuments = splitter.apply(Collections.singletonList(document));

            log.info("开始提取{}个分块的关键词", splitDocuments.size());
            for (Document doc : splitDocuments) {
                // === 移除嵌套的addDocs方法，直接设置元数据 ===
                doc.getMetadata().put(METADATA_CHUNK_SIZE, String.valueOf(CHUNK_SIZE));
                doc.getMetadata().put(METADATA_KNOWLEDGE_ID, String.valueOf(chatKnowledge.getKnowledgeId()));

                // 添加调试日志验证元数据设置
                log.debug("分块{}元数据: chunkSize={}, knowledgeId={}",
                        splitDocuments.indexOf(doc) + 1,
                        doc.getMetadata().get(METADATA_CHUNK_SIZE),
                        doc.getMetadata().get(METADATA_KNOWLEDGE_ID));

                // 添加分块内容调试日志
                String chunkPreview = doc.getText().length() > 50 ? doc.getText().substring(0, 50) + "..." : doc.getText();
                log.debug("分块{}内容预览: {}", splitDocuments.indexOf(doc) + 1, chunkPreview);

                String keywords = extractKeywords(doc.getText());
                if (StringUtils.isNotEmpty(keywords)) {
                    doc.getMetadata().put("keywords", keywords);
                    log.debug("提取关键词: {}", keywords);
                } else {
                    log.warn("分块{}关键词提取失败", splitDocuments.indexOf(doc) + 1);
                }
            }

            log.info("准备写入向量库 - 分块数: {}", splitDocuments.size());
            ollamaVectorStore.add(splitDocuments);
            log.info("向量库写入完成");

        } catch (Exception e) {
            log.error("文档处理失败", e);
        }
    }

    private String extractKeywords(String text) {
        try {
            String result = ChatClient.create(ollamaChatModel)
                    .prompt()
                    .user(keywordPromptTemplate.render(Map.of("text", text)))
                    .call()
                    .content();

            return result.replaceAll("[^\\w\\s\u4e00-\u9fa5]", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        } catch (Exception e) {
            log.error("关键词提取失败", e);
            return null;
        }
    }

    public class QueryRewriter {
        private final QueryTransformer transformer;

        public QueryRewriter(ChatModel model, PromptTemplate template) {
            // 严格按照图片中的方法调用顺序和参数
            this.transformer = RewriteQueryTransformer.builder()
                    .chatClientBuilder(ChatClient.builder(model))
                    .promptTemplate(template)
                    .targetSearchSystem("default") // 保留targetSearchSystem设置
                    .build();
        }

        public String rewrite(String query) {
            try {
                log.debug("原始查询: {}", query);
                String rewritten = transformer.transform(new Query(query)).text();

                String cleaned = rewritten.replaceAll("[^\\w\\s\u4e00-\u9fa5]", "")
                        .replaceAll("\\s+", " ")
                        .trim();

                log.debug("重写结果: {} -> {}", query, cleaned);
                return cleaned.isEmpty() ? query : cleaned;
            } catch (Exception e) {
                log.error("查询重写失败，使用原始查询", e);
                return query;
            }
        }
    }

    @Override
    public List<Document> similaritySearch(QueryVo queryVo) {
        try {
            String rewrittenQuery = queryRewriter.rewrite(queryVo.getMsg());
            log.info("查询重写结果: '{}' -> '{}'", queryVo.getMsg(), rewrittenQuery);

            SearchRequest request = SearchRequest.builder()
                    .query(rewrittenQuery)
                    .filterExpression(new FilterExpressionBuilder()
                            .eq("projectId", queryVo.getProjectId().toString())
                            .build())
                    .topK(5)
                    .similarityThreshold(0.5f) // 提高阈值
                    .build();

            List<Document> documents = this.ollamaVectorStore.similaritySearch(request);

            documents = documents.stream()
                    .filter(doc -> queryVo.getProjectId().toString()
                            .equals(doc.getMetadata().get("projectId")))
                    .filter(doc -> doc.getScore() != null && doc.getScore() > 0.5)
                    .sorted((d1, d2) -> Double.compare(d2.getScore(), d1.getScore()))
                    .limit(5)
                    .collect(Collectors.toList());

            log.info("检索完成 - 有效文档数: {}", documents.size());

            // 添加详细文档信息输出
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                String text = doc.getText() != null ? doc.getText() : "";
                String preview = text.length() > 100 ? text.substring(0, 100) + "..." : text;
                String chunkSize = (String) doc.getMetadata().getOrDefault("chunkSize", "未知");

                // 手动格式化相似度分数，避免日志框架格式化问题
                double score = doc.getScore() != null ? doc.getScore() : 0;
                String formattedScore = String.format("%.4f", score);

                // 添加调试日志，监控文档存储完整性
                if (text.isEmpty()) {
                    log.warn("文档{}内容为空，知识ID: {}", i+1, doc.getMetadata().get("knowledgeId"));
                }
                if ("未知".equals(chunkSize)) {
                    log.warn("文档{}分块大小元数据缺失，知识ID: {}", i+1, doc.getMetadata().get("knowledgeId"));
                }

                log.info("文档{}: 相似度={}, 分块大小={}, 内容预览={}",
                        i + 1,
                        formattedScore,
                        chunkSize,
                        preview);
            }

            return documents;
        } catch (Exception e) {
            log.error("相似度检索失败", e);
            return Collections.emptyList();
        }
    }

    private List<Document> retrievedDocuments;

    @Override
    public Flux<String> chat_stream(Message[] messages) {
        try {
            StringBuilder systemPrompt = new StringBuilder("""
                    你是一个严格基于桂林电子科技大学北海校区知识库内容回答问题的AI助手，请遵守以下规则:
                    
                    1. 回答必须基于提供的知识库内容,如果用户询问的内容是知识库内容有的,把用户提问想了解的的内容在知识库里找到后把相关的都回答出来,不相关的不用回答(即使没有直接找到知识库的内容但是可能与用户的提问间接相关)
                    2. 引用原文时必须注明出处(如"根据文档1...")
                    3. 如果问题超出知识库范围，请回答"根据现有资料无法回答该问题"
                    
                    当前知识库内容:
                    """);

            if (retrievedDocuments != null && !retrievedDocuments.isEmpty()) {
                for (int i = 0; i < retrievedDocuments.size(); i++) {
                    Document doc = retrievedDocuments.get(i);
                    if (doc != null && StringUtils.hasText(doc.getText())) {
                        systemPrompt.append("\n\n--- 文档").append(i + 1).append(" ---");
                        systemPrompt.append("\n").append(doc.getText());
                    }
                }
            } else {
                systemPrompt.append("\n无相关内容");
            }

            Message systemMessage = new SystemMessage(systemPrompt.toString());

            List<Message> messageList = new ArrayList<>();
            messageList.add(systemMessage);

            List<Message> userMessages = Arrays.stream(messages)
                    .filter(m -> m.getMessageType() == MessageType.USER)
                    .collect(Collectors.toList());

            if (!userMessages.isEmpty()) {
                messageList.add(userMessages.get(userMessages.size() - 1));
            }

            return ollamaChatModel.stream(messageList.toArray(new Message[0]))
                    .doOnError(e -> log.error("聊天流处理错误", e));
        } catch (Exception e) {
            log.error("构建聊天流失败", e);
            return Flux.error(e);
        }
    }

    public void setRetrievedDocuments(List<Document> documents) {
        this.retrievedDocuments = documents != null ?
                new ArrayList<>(documents) : Collections.emptyList();
    }
}