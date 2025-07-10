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
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
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

/**
 * ollama的具体策略类
 */
@AiBean(SystemConstant.MODEL_TYPE_OLLAMA)
public class OllamaAiOperator implements AiOperator {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiOperator.class);

    @Autowired
    private QdrantVectorStore ollamaVectorStore;

    @Autowired
    private OllamaChatModel ollamaChatModel;

    private KeywordMetadataEnricher keywordEnricher;
    private QueryRewriter queryRewriter;

    @PostConstruct
    public void init() {
        try {
            // 确保ollamaChatModel已注入
            if (ollamaChatModel == null) {
                throw new IllegalArgumentException("ollamaChatModel cannot be null");
            }

            // 初始化关键词增强器（参考RAG进阶中的MyKeywordEnricher实现）
            this.keywordEnricher = new KeywordMetadataEnricher(ollamaChatModel, 5);
            this.queryRewriter = new QueryRewriter(ollamaChatModel);
            log.info("QueryRewriter and KeywordEnricher initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize components", e);
            throw new RuntimeException("Initialization failed", e);
        }
    }

    @Override
    public void addDocs(ChatKnowledge chatKnowledge) {
        try {
            // 新增：方法入口日志
            log.info("=== 开始处理文档添加: {} (项目ID: {}) ===",
                    chatKnowledge.getFileName(), chatKnowledge.getProjectId());

            // 优化：添加内容长度检查
            if (StringUtils.isEmpty(chatKnowledge.getContent())) {
                log.error("文档内容为空，无法添加: {}", chatKnowledge.getFileName());
                return;
            }

            if (chatKnowledge.getContent().length() < 50) {
                log.warn("文档内容过短({}字符)，跳过添加: {}",
                        chatKnowledge.getContent().length(), chatKnowledge.getFileName());
                return;
            }

            // 手动创建文档
            Document document = new Document(chatKnowledge.getContent());
            log.debug("成功创建文档对象: {}", chatKnowledge.getFileName());

            // 增强元数据 - 符合RAG进阶中的最佳实践
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("projectId", chatKnowledge.getProjectId().toString());
            metadata.put("knowledgeId", chatKnowledge.getKnowledgeId().toString());
            metadata.put("fileName", chatKnowledge.getFileName());
            metadata.put("contentLength", chatKnowledge.getContent().length());
            metadata.put("timestamp", String.valueOf(System.currentTimeMillis())); // 将Long类型转换为String
            document.getMetadata().putAll(metadata);

            // 文档分块（参考RAG基础中的TokenTextSplitter使用方法）
            TokenTextSplitter splitter = new TokenTextSplitter(2000, 300, 10, 5000, true); // 增大chunkSize至2000
            List<Document> splitDocuments = splitter.apply(Collections.singletonList(document));

            // 新增分块详情日志
            log.info("文档分块配置 - 块大小: {}, 最小块字符数: {}, 重叠token数: {}", 800, 10, 200);
            log.info("文档分块完成 - 原始文档长度: {}字符, 分块数量: {}, 设定块大小: {} tokens, 重叠: {} tokens",
                    chatKnowledge.getContent().length(),
                    splitDocuments.size(),
                    300, // 实际使用的chunkSize
                    100); // 实际使用的chunkOverlap

            // 输出前3个分块的基本信息（避免日志过长）
            for (int i = 0; i < Math.min(3, splitDocuments.size()); i++) {
                Document chunk = splitDocuments.get(i);
                String preview = chunk.getText().length() > 80 ? chunk.getText().substring(0, 80) + "..." : chunk.getText();
                log.debug("分块{} - 长度: {}字符, 内容预览: {}",
                        i+1, chunk.getText().length(), preview);
            }

            // 添加关键词元数据增强（RAG进阶中的最佳实践）
            splitDocuments = keywordEnricher.apply(splitDocuments);

            // 添加前检查块数量
            if (splitDocuments.isEmpty()) {
                log.error("文档拆分后为空: {}", chatKnowledge.getFileName());
                return;
            }

            log.info("准备添加文档: {}，项目ID: {}，分块数: {}",
                    chatKnowledge.getFileName(),
                    chatKnowledge.getProjectId(),
                    splitDocuments.size());

            ollamaVectorStore.add(splitDocuments);
            // 新增添加结果验证日志
            log.info("向量库添加完成 - 请求添加: {}个分块", splitDocuments.size());

            // 验证添加结果（修改现有验证逻辑）
            List<Document> verifyDocs = ollamaVectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(chatKnowledge.getFileName())
                    .filterExpression(new FilterExpressionBuilder()
                        .eq("knowledgeId", chatKnowledge.getKnowledgeId().toString())
                        .build())
                    .topK(splitDocuments.size()) // 验证所有分块
                    .similarityThreshold(0.1f) // 降低阈值确保能检索到
                    .build());

            if (verifyDocs.size() == splitDocuments.size()) {
                log.info("✅ 所有分块成功添加到向量库");
            } else if (!verifyDocs.isEmpty()) {
                log.warn("⚠️ 部分分块添加失败 - 请求: {}个, 实际添加: {}个",
                        splitDocuments.size(), verifyDocs.size());
            } else {
                log.error("❌ 所有分块添加失败");
            }
            if (verifyDocs.isEmpty()) {
                log.error("=== 文档添加验证失败: {} ===", chatKnowledge.getFileName());
            } else {
                log.info("=== 文档添加验证成功: {} ===", chatKnowledge.getFileName());
            }
            log.info("=== 成功添加文档到向量库: {} ({}个分块) ===",
                    chatKnowledge.getFileName(), splitDocuments.size());
        } catch (Exception e) {
            log.error("=== 文档添加失败: {} ===", e);
            log.error("添加文档到向量库失败: {}", chatKnowledge.getFileName(), e);
        }
    }

    // 查询重写器内部类 - 优化查询重写逻辑
    // 查询重写器内部类 - 优化查询重写逻辑
    public class QueryRewriter {
        private final QueryTransformer queryTransformer;

        public QueryRewriter(ChatModel chatModel) {
            try {
                if (chatModel == null) {
                    throw new IllegalArgumentException("ChatModel cannot be null");
                }

                // 使用ChatClient.Builder构建（参考RAG基础中的QueryRewriter实现）
                ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);

                // 优化提示词模板 - 更符合RAG最佳实践
                this.queryTransformer = new RewriteQueryTransformer(
                        chatClientBuilder,
                        new PromptTemplate("""
                            任务: 将用户查询重写为更适合向量数据库检索的形式
                            原始查询: {query}
                            领域: {target}
                            
                            要求:
                            1. 保留所有核心概念和关键术语
                            2. 保持查询的核心意图（如保留疑问词）
                            3. 长度控制在50字以内
                            4. 不添加额外信息
                            
                            重写结果:
                        """),
                        "system"
                );
                log.debug("QueryRewriter initialized successfully");
            } catch (Exception e) {
                log.error("QueryRewriter construction failed", e);
                throw new RuntimeException("QueryRewriter construction failed", e);
            }
        }

        public String rewrite(String prompt) {
            try {
                String rewritten = queryTransformer.transform(new Query(prompt)).text();

                // 结果清理
                String cleaned = rewritten.replace("关键词:", "")
                        .replace("原始查询:", "")
                        .replace("领域:", "")
                        .trim();

                // 如果包含换行符，只取第一行
                if (cleaned.contains("\n")) {
                    cleaned = cleaned.substring(0, cleaned.indexOf('\n')).trim();
                }

                // 移除编号和多余符号
                cleaned = cleaned.replaceAll("\\d+\\.\\s*", "") // 移除数字编号
                        .replaceAll("^[,\\s]+", "")   // 移除开头的逗号和空格
                        .replaceAll("[,\\s]+$", "");  // 移除结尾的逗号和空格

                log.debug("清理后的重写结果: {}", cleaned);
                return cleaned;
            } catch (Exception e) {
                log.error("查询重写失败，使用原始查询", e);
                return prompt;
            }
        }
    }

    @Override
    public List<Document> similaritySearch(QueryVo queryVo) {
        try {
            String rewrittenQuery = queryRewriter.rewrite(queryVo.getMsg());
            log.info("查询重写: '{}' -> '{}'", queryVo.getMsg(), rewrittenQuery);

            // 构建检索请求，增加领域元数据过滤
            SearchRequest request = SearchRequest.builder()
                    .query(rewrittenQuery)
                    .filterExpression(new FilterExpressionBuilder()
                            .eq("projectId", queryVo.getProjectId().toString())
                            .build())
                    .topK(10) // 增加召回数量
                    .similarityThreshold(0.35f) // 降低相似度阈值
                    .build();

            List<Document> documents = this.ollamaVectorStore.similaritySearch(request);

            // 多维度过滤和排序（RAG进阶中的后处理策略）
            documents = documents.stream()
                    .filter(doc -> {
                        String docProjectId = (String) doc.getMetadata().get("projectId");
                        return queryVo.getProjectId().toString().equals(docProjectId);
                    })
                    .filter(doc -> doc.getScore() != null && doc.getScore() > 0.3)
                    // 优先保留包含关键词的文档
                    .sorted((d1, d2) -> {
                        double scoreDiff = Double.compare(d2.getScore(), d1.getScore());
                        if (scoreDiff != 0) return (int) scoreDiff;
                        // 有关键词元数据的文档优先
                        boolean hasKeywords1 = d1.getMetadata().containsKey("keywords");
                        boolean hasKeywords2 = d2.getMetadata().containsKey("keywords");
                        return Boolean.compare(hasKeywords2, hasKeywords1);
                    })
                    .limit(5) // 适当增加返回文档数量
                    .collect(Collectors.toList());

            // 项目ID过滤
            documents = documents.stream()
                    .filter(doc -> {
                        String docProjectId = (String) doc.getMetadata().get("projectId");
                        return queryVo.getProjectId().toString().equals(docProjectId);
                    })
                    .filter(doc -> doc.getScore() != null && doc.getScore() > 0.3)
                    .sorted((d1, d2) -> Double.compare(d2.getScore(), d1.getScore())) // 按相似度降序
                    .limit(5) // 只取最相关5条
                    .collect(Collectors.toList());

            // 详细日志输出（去除文件名显示）
            log.info("检索结果统计 - 查询: {}, 项目ID: {}, 有效文档数: {}",
                    queryVo.getMsg(), queryVo.getProjectId(), documents.size());

            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                String contentPreview = doc.getText().length() > 100
                        ? doc.getText().substring(0, 100) + "..."
                        : doc.getText();
                // 新增分块元数据日志
                log.info("匹配文档{} - 相似度: {:.4f}, 分块大小: {}字符, 知识ID: {}",
                        i + 1,
                        doc.getScore(),
                        doc.getText().length(),
                        doc.getMetadata().get("knowledgeId"));
                log.debug("文档{}内容预览: {}", i+1, contentPreview);
            }

            return documents;
        } catch (Exception e) {
            log.error("相似度搜索失败", e);
            return Collections.emptyList();
        }
    }

    private List<Document> retrievedDocuments;

    @Override
    public Flux<String> chat_stream(Message[] messages) {
        try {
            // 构建严格的系统提示
            StringBuilder systemPrompt = new StringBuilder("""
                    你是一个严格基于提供知识库内容回答问题的AI助手，必须遵守以下规则:
                                    
                    1. 回答范围限制:
                    - 仅使用提供的知识库内容回答问题
                    - 禁止使用任何外部知识或推测
                    - 如果问题超出知识库范围，必须回答"根据提供资料，无法回答此问题"
                                    
                    2. 内容提取要求:
                    - 必须精确引用知识库中的原文内容
                    - 涉及具体条款、章节时，必须注明出处(如"根据文档1第X章...")
                                    
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

            // 只保留最新的用户消息
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
        this.retrievedDocuments = documents != null ? new ArrayList<>(documents) : Collections.emptyList();
    }
}