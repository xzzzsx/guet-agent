package com.atguigu.guliai.strategy;
import com.atguigu.common.utils.StringUtils;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.vo.QueryVo;
import com.atguigu.system.domain.ChatKnowledge;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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

    @Autowired
    private QdrantVectorStore ollamaVectorStore;
    @Autowired
    private OllamaChatModel ollamaChatModel;

    private QueryTransformer queryTransformer;
    private KeywordMetadataEnricher keywordMetadataEnricher;
    private RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
    private List<Document> retrievedDocuments; // 添加检索到的文档列表

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
                    .template("您提问的问题位于我的知识库之外，我无法回答您提问的问题。请尝试重新表述您的问题或联系学校相关部门获取帮助。")
                    .build();

            // 创建上下文查询增强器
            ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                    .allowEmptyContext(false)
                    .emptyContextPromptTemplate(emptyContextPromptTemplate)
                    .build();

            // 移除硬编码的DocumentRetriever创建
            // 创建RAG检索增强顾问 - 使用简化的方式，不预创建DocumentRetriever
            this.retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(VectorStoreDocumentRetriever.builder()
                            .vectorStore(ollamaVectorStore)
                            .similarityThreshold(0.45)
                            .topK(3)
                            .build())
                    .queryAugmenter(queryAugmenter)
                    .build();

            // 初始化检索到的文档列表
            this.retrievedDocuments = new ArrayList<>();
            log.info("组件初始化成功");
        } catch (Exception e) {
            log.error("组件初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
    }

    /**
     * 执行查询重写
     *
     * @param prompt
     * @return
     */
    public String doQueryRewrite(String prompt) {
        Query query = new Query(prompt);
        // 执行查询重写
        Query transformedQuery = queryTransformer.transform(query);
        // 输出重写后的查询
        return transformedQuery.text();
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

            log.info("开始为{}个分块提取关键词", splitDocuments.size());

            // 使用KeywordMetadataEnricher为每个分块添加关键词
            for (Document doc : splitDocuments) {
                doc.getMetadata().put(METADATA_CHUNK_SIZE, String.valueOf(CHUNK_SIZE));
                doc.getMetadata().put(METADATA_KNOWLEDGE_ID, String.valueOf(chatKnowledge.getKnowledgeId()));
            }

            // 使用KeywordMetadataEnricher为所有分块添加关键词元数据
            List<Document> enrichedDocuments = keywordMetadataEnricher.transform(splitDocuments);

            // 控制台输出提取的关键词
            for (int i = 0; i < enrichedDocuments.size(); i++) {
                Document doc = enrichedDocuments.get(i);
                String keywords = (String) doc.getMetadata().get("keywords");
                if (keywords != null) {
                    System.out.println("控制台输出 - 分块 " + (i + 1) + " 关键词: " + keywords);
                }
            }

            log.info("关键词提取完成，准备写入向量库 - 分块数: {}", enrichedDocuments.size());
            ollamaVectorStore.add(enrichedDocuments);
            log.info("向量库写入完成");
        } catch (Exception e) {
            log.error("文档处理失败", e);
        }
    }

    @Override
    public List<Document> similaritySearch(QueryVo queryVo) {
        try {
            if (queryTransformer == null) {
                log.error("queryTransformer未初始化，使用原始查询");
                return searchWithOriginalQuery(queryVo);
            }

            // 使用doQueryRewrite方法进行查询重写
            String rewrittenQuery = doQueryRewrite(queryVo.getMsg());
            log.info("查询重写结果: '{}' -> '{}'", queryVo.getMsg(), rewrittenQuery);

            SearchRequest request = buildSearchRequest(queryVo, rewrittenQuery);
            List<Document> documents = this.ollamaVectorStore.similaritySearch(request);

            // 处理检索结果，如果为空则使用空上下文提示
            if (documents.isEmpty()) {
                log.info("未检索到相关文档，将使用空上下文提示");
            }

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
                .similarityThreshold(0.45)
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
        log.info("文档{}: 相似度={}, 分块大小={}, 内容预览={}",
                index,
                String.format("%.4f", score),
                chunkSize,
                preview);
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

            // 使用RetrievalAugmentationAdvisor处理聊天
            ChatClient chatClient = ChatClient.builder(ollamaChatModel)
                    .defaultAdvisors(retrievalAugmentationAdvisor)
                    .build();

            // 构建系统提示
            StringBuilder systemPrompt = new StringBuilder("""
                你是一个严格基于桂林电子科技大学北海校区知识库内容回答问题的AI助手，请遵守以下规则:
                                    
                1. 回答必须基于提供的知识库内容,如果用户询问的内容是知识库内容有的,把用户提问想了解的的内容在知识库里找到后把相关的都回答出来,不相关的不用回答(即使没有直接找到知识库的内容但是可能与用户的提问间接相关)
                2. 引用原文时必须注明出处(如"根据文档1...")
                """);

            Message systemMessage = new SystemMessage(systemPrompt.toString());
            List<Message> messageList = new ArrayList<>();
            messageList.add(systemMessage);

            if (!userMessages.isEmpty()) {
                messageList.add(userMessages.get(userMessages.size() - 1));
            }

            return chatClient.prompt()
                    .messages(messageList)
                    .stream()
                    .content()
                    .doOnError(e -> log.error("聊天流处理错误", e));
        } catch (Exception e) {
            log.error("构建聊天流失败", e);
            return Flux.error(e);
        }
    }

    /**
     * 设置检索到的文档
     * @param documents 检索到的文档列表
     */
    public void setRetrievedDocuments(List<Document> documents) {
        this.retrievedDocuments = documents != null ?
                new ArrayList<>(documents) : Collections.emptyList();
    }

    /**
     * 测试查询重写效果：对比原始查询和重写查询的真实相似度
     */
    public void testQueryRewriteSimilarity() {
        System.out.println("=== 查询重写效果测试（真实相似度对比） ===");

        // 测试用例
        String[] testQueries = {
                "你们学校的校区情况怎么样，环境好吗？我着急想知道呀!",
                "教室凉快吗,因为北海会很热的呢听说,热的话受不了啊!",
                "普通话考试你们学校有考点吗?普通话考试对于我来说很重要的呀,这是必须要考的呢,妈妈告诉我!",
                "你们学校周边出行情况怎么样,方便吗?",
                "我如果有事情需要请假的话怎么个流程呢,你能告诉我吗,我真的很想知道呀,需要打电话确认吗还是什么呢?",
                "你们上课时间是不是很早丫,我可能起不起来呢"
        };

        for (String originalQuery : testQueries) {
            try {
                // 获取重写后的查询
                String rewrittenQuery = doQueryRewrite(originalQuery);

                // 使用原始查询搜索
                SearchRequest originalRequest = SearchRequest.builder()
                        .query(originalQuery)
                        .topK(3)
                        .similarityThreshold(0.45)
                        .build();
                List<Document> originalResults = this.ollamaVectorStore.similaritySearch(originalRequest);

                // 使用重写查询搜索
                SearchRequest rewrittenRequest = SearchRequest.builder()
                        .query(rewrittenQuery)
                        .topK(3)
                        .similarityThreshold(0.45)
                        .build();
                List<Document> rewrittenResults = this.ollamaVectorStore.similaritySearch(rewrittenRequest);

                // 计算最高相似度
                double originalMaxScore = originalResults.stream()
                        .mapToDouble(doc -> doc.getScore() != null ? doc.getScore() : 0.0)
                        .max().orElse(0.0);

                double rewrittenMaxScore = rewrittenResults.stream()
                        .mapToDouble(doc -> doc.getScore() != null ? doc.getScore() : 0.0)
                        .max().orElse(0.0);

                System.out.println("\n原始查询: " + originalQuery);
                System.out.println("原始查询相似度: " + String.format("%.4f", originalMaxScore));
                System.out.println("重写查询: " + rewrittenQuery);
                System.out.println("重写查询相似度: " + String.format("%.4f", rewrittenMaxScore));

                if (rewrittenMaxScore > originalMaxScore) {
                    System.out.println("提升: +" + String.format("%.4f", rewrittenMaxScore - originalMaxScore));
                } else if (rewrittenMaxScore < originalMaxScore) {
                    System.out.println("下降: -" + String.format("%.4f", originalMaxScore - rewrittenMaxScore));
                } else {
                    System.out.println("无变化");
                }
            } catch (Exception e) {
                System.err.println("测试查询失败: " + originalQuery + " - " + e.getMessage());
            }
        }
        System.out.println("\n=== 测试完成 ===");
    }

    /**
     * 计算两个文本的相似度（简单实现）
     */
    private double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }

        Set<String> tokens1 = getTokens(text1);
        Set<String> tokens2 = getTokens(text2);

        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }

        // 计算交集
        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        // 计算并集
        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);

        // Jaccard相似度
        return (double) intersection.size() / union.size();
    }

    /**
     * 简单的中文分词（按字符分割）
     */
    private Set<String> getTokens(String text) {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || Character.isIdeographic(c)) {
                tokens.add(String.valueOf(c));
            }
        }
        return tokens;
    }
}