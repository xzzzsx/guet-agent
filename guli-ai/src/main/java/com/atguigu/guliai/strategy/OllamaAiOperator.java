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

    private QueryRewriter queryRewriter;

    @PostConstruct
    public void init() {
        try {
            // 确保ollamaChatModel已注入
            if (ollamaChatModel == null) {
                throw new IllegalArgumentException("ollamaChatModel cannot be null");
            }

            this.queryRewriter = new QueryRewriter(ollamaChatModel);
            log.info("QueryRewriter initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize QueryRewriter", e);
            throw new RuntimeException("QueryRewriter initialization failed", e);
        }
    }

    @Override
    public void addDocs(ChatKnowledge chatKnowledge) {
        try {
            // 优化：添加内容长度检查
            if (StringUtils.isEmpty(chatKnowledge.getContent()) || chatKnowledge.getContent().length() < 50) {
                log.warn("文档内容过短或为空，跳过添加: {}", chatKnowledge.getFileName());
                return;
            }

            Document document = new Document(chatKnowledge.getContent());

            // 增强元数据 - 确保所有必需字段
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("projectId", chatKnowledge.getProjectId().toString());
            metadata.put("knowledgeId", chatKnowledge.getKnowledgeId().toString());
            metadata.put("fileName", chatKnowledge.getFileName());  // 确保fileName不为空
            metadata.put("contentLength", chatKnowledge.getContent().length());

            // 正确的元数据设置方式
            document.getMetadata().putAll(metadata);

            // 优化拆分策略 - 增大块大小保证章节完整性
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(2500)  // 增大块大小至2500
                    .withMinChunkSizeChars(800)
                    .withKeepSeparator(true)
                    .build();

            List<Document> splitDocuments = splitter.apply(Collections.singletonList(document));

            // 添加前检查块数量
            if (splitDocuments.isEmpty()) {
                log.warn("文档拆分后为空: {}", chatKnowledge.getFileName());
                return;
            }

            ollamaVectorStore.add(splitDocuments);
            log.info("成功添加文档到向量库: {} ({} chunks)", chatKnowledge.getFileName(), splitDocuments.size());
        } catch (Exception e) {
            log.error("添加文档到向量库失败: {}", chatKnowledge.getFileName(), e);
        }
    }

    // 查询重写器内部类 - 优化查询重写逻辑
    public class QueryRewriter {
        private final QueryTransformer queryTransformer;

        public QueryRewriter(ChatModel chatModel) {
            try {
                if (chatModel == null) {
                    throw new IllegalArgumentException("ChatModel cannot be null");
                }

                // 使用ChatClient.Builder构建
                ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);

                // 优化提示词模板 - 强制要求精准匹配培养方案的关键部分
                this.queryTransformer = new RewriteQueryTransformer(
                        chatClientBuilder,
                        new PromptTemplate("""
                            请提取培养方案文件的核心关键词:
                            原始查询: {query}
                            领域: {target}
                            
                            要求:
                            1. 重点提取: 专业名称、主干学科、核心课程、毕业要求
                            2. 忽略年份、校区名称等次要信息
                            3. 输出格式: 专业名称+核心术语(如"网络工程 主干学科")
                            4. 每个术语必须是完整短语
                            
                            关键词:"""),
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

            // 使用原有的filterExpression写法 - 保持不变
            SearchRequest request = SearchRequest.builder()
                    .query(rewrittenQuery)
                    .filterExpression(new FilterExpressionBuilder()
                            .eq("projectId", queryVo.getProjectId().toString())
                            .build())
                    .topK(10) // 增加召回数量确保捕获核心内容
                    .similarityThreshold(0.3f) // 降低阈值保证完整段落被召回
                    .build();

            List<Document> documents = this.ollamaVectorStore.similaritySearch(request);

            // 项目ID过滤
            documents = documents.stream()
                    .filter(doc -> {
                        String docProjectId = (String) doc.getMetadata().get("projectId");
                        return queryVo.getProjectId().toString().equals(docProjectId);
                    })
                    .filter(doc -> doc.getScore() != null && doc.getScore() > 0.3)
                    .sorted((d1, d2) -> Double.compare(d2.getScore(), d1.getScore())) // 按相似度降序
                    .limit(3) // 只取最相关3条
                    .collect(Collectors.toList());

            // 详细日志输出（去除文件名显示）
            log.info("检索结果统计 - 查询: {}, 项目ID: {}, 有效文档数: {}",
                    queryVo.getMsg(), queryVo.getProjectId(), documents.size());

            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                String contentPreview = doc.getText().length() > 50
                        ? doc.getText().substring(0, 50) + "..."
                        : doc.getText();

                log.info("文档{} - 相似度: {} - 内容: {}",
                        i + 1, doc.getScore(), contentPreview);
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
                        systemPrompt.append("\n\n--- 文档").append(i + 1).append(" ---\n");

                        // 使用知识库ID代替文件名
                        String docId = doc.getId() != null
                                ? "知识块" + doc.getId().substring(0, 4)
                                : "文档" + (i + 1);
                        systemPrompt.append(docId).append(":\n");

                        // 完整显示文档内容（不截断）
                        systemPrompt.append(doc.getText());
                    }
                }
            } else {
                systemPrompt.append("\n无相关内容");
            }

            Message systemMessage = new SystemMessage(systemPrompt.toString());

            List<Message> messageList = new ArrayList<>();
            messageList.add(systemMessage);
            messageList.addAll(Arrays.asList(messages));

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