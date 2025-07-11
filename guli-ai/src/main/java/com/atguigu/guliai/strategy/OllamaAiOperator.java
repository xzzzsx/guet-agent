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
    private static final String METADATA_CHUNK_SIZE = "chunkSize";
    private static final String METADATA_KNOWLEDGE_ID = "knowledgeId";
    private static final int CHUNK_SIZE = 500;

    @Autowired
    private QdrantVectorStore ollamaVectorStore;

    @Autowired
    private OllamaChatModel ollamaChatModel;

    private QueryRewriter queryRewriter;

    @PostConstruct
    public void init() {
        log.info("=== 开始初始化OllamaAiOperator组件 ===");
        try {
            if (ollamaChatModel == null) {
                throw new IllegalArgumentException("ollamaChatModel 不能为 null");
            }

            // 初始化queryRewriter
            this.queryRewriter = new QueryRewriter(ollamaChatModel);
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

            TokenTextSplitter splitter = new TokenTextSplitter(CHUNK_SIZE, 100, 10, 5000, true);
            List<Document> splitDocuments = splitter.apply(Collections.singletonList(document));

            log.info("开始提取{}个分块的关键词", splitDocuments.size());
            for (Document doc : splitDocuments) {
                doc.getMetadata().put(METADATA_CHUNK_SIZE, String.valueOf(CHUNK_SIZE));
                doc.getMetadata().put(METADATA_KNOWLEDGE_ID, String.valueOf(chatKnowledge.getKnowledgeId()));

                log.debug("分块{}元数据: chunkSize={}, knowledgeId={}",
                        splitDocuments.indexOf(doc) + 1,
                        doc.getMetadata().get(METADATA_CHUNK_SIZE),
                        doc.getMetadata().get(METADATA_KNOWLEDGE_ID));

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
            String prompt = """
                    请从文本中提取最具检索价值的关键词：
                    1. 提取3-5个最能代表文本内容的关键词
                    2. 可以是名词、动词或形容词
                    3. 关键词长度不限但需精炼
                    4. 必须用单个空格分隔
                    5. 示例：
                       输入：学校东区晚上有很多小吃摊
                       输出：东区 夜间餐饮 小吃摊
                       
                    文本内容：{text}
                                    
                    关键词：""";

            PromptTemplate template = new PromptTemplate(prompt);
            String result = ChatClient.create(ollamaChatModel)
                    .prompt()
                    .user(template.render(Map.of("text", text)))
                    .call()
                    .content();

            // 格式化处理
            return result.replace("输出", "")  // 新增清理
                    .replaceAll("[^\\w\u4e00-\u9fa5]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
        } catch (Exception e) {
            log.error("关键词提取失败，使用备用方案", e);
            // 备用方案：按标点分割取前5个非空短语
            return Arrays.stream(text.split("[，。；！？、]"))
                    .filter(s -> !s.isBlank())
                    .limit(5)
                    .map(s -> s.trim().replaceAll("[^\\u4e00-\\u9fa5]", ""))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(" "));
        }
    }

    public class QueryRewriter {
        private final ChatModel chatModel;

        public QueryRewriter(ChatModel model) {
            this.chatModel = model;
        }

        public String rewrite(String query) {
            try {
                String prompt = """
                        请严格按以下要求转换查询关键词：
                                    
                        ## 要求
                        1. 只输出最终关键词，不要包含"输出"等前缀
                        2. 用空格分隔3-5个关键词
                        3. 示例格式：
                           输入：校区环境怎么样？
                           校区环境 教学设施 卫生条件
                           
                           输入：宿舍条件好吗？
                           宿舍条件 住宿环境 生活设施
                                    
                        ## 待转换查询
                        {query}
                                    
                        ## 关键词结果
                        """;  // 注意这里没有冒号

                String rewritten = ChatClient.create(chatModel)
                        .prompt()
                        .user(new PromptTemplate(prompt).render(Map.of("query", query)))
                        .call()
                        .content();

                return cleanKeywords(rewritten);
            } catch (Exception e) {
                log.error("查询重写失败，使用原始查询", e);
                return query;
            }
        }

        private String cleanKeywords(String raw) {
            return raw.replace("输出", "")  // 移除可能的"输出"字样
                    .replaceAll("[^\\w\u4e00-\u9fa5]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
        }
    }

    @Override
    public List<Document> similaritySearch(QueryVo queryVo) {
        try {
            // 添加null检查
            if (queryRewriter == null) {
                log.error("queryRewriter未初始化，使用原始查询");
                return searchWithOriginalQuery(queryVo);
            }

            String rewrittenQuery = queryRewriter.rewrite(queryVo.getMsg());
            log.info("查询重写结果: '{}' -> '{}'", queryVo.getMsg(), rewrittenQuery);

            SearchRequest request = buildSearchRequest(queryVo, rewrittenQuery);
            List<Document> documents = this.ollamaVectorStore.similaritySearch(request);

            return processSearchResults(documents, queryVo);
        } catch (Exception e) {
            log.error("相似度检索失败", e);
            return Collections.emptyList();
        }
    }

    private List<Document> searchWithOriginalQuery(QueryVo queryVo) {
        SearchRequest request = buildSearchRequest(queryVo, queryVo.getMsg());
        return processSearchResults(
                this.ollamaVectorStore.similaritySearch(request),
                queryVo
        );
    }

    private SearchRequest buildSearchRequest(QueryVo queryVo, String query) {
        return SearchRequest.builder()
                .query(query)
                .filterExpression(new FilterExpressionBuilder()
                        .eq("projectId", queryVo.getProjectId().toString())
                        .build())
                .topK(3)
                .similarityThreshold(0.5f)
                .build();
    }

    private List<Document> processSearchResults(List<Document> documents, QueryVo queryVo) {
        return documents.stream()
                .filter(doc -> queryVo.getProjectId().toString()
                        .equals(doc.getMetadata().get("projectId")))
                .sorted((d1, d2) -> Double.compare(d2.getScore(), d1.getScore()))
                .limit(5)
                .peek(doc -> logDocumentInfo(doc, documents.indexOf(doc) + 1))
                .collect(Collectors.toList());
    }

    private void logDocumentInfo(Document doc, int index) {
        String text = doc.getText() != null ? doc.getText() : "";
        String preview = text.length() > 100 ? text.substring(0, 100) + "..." : text;
        String chunkSize = (String) doc.getMetadata().getOrDefault("chunkSize", "未知");
        double score = doc.getScore() != null ? doc.getScore() : 0;

        if (text.isEmpty()) {
            log.warn("文档{}内容为空，知识ID: {}", index, doc.getMetadata().get("knowledgeId"));
        }
        if ("未知".equals(chunkSize)) {
            log.warn("文档{}分块大小元数据缺失，知识ID: {}", index, doc.getMetadata().get("knowledgeId"));
        }

        log.info("文档{}: 相似度={}, 分块大小={}, 内容预览={}",
                index,
                String.format("%.4f", score),
                chunkSize,
                preview);
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