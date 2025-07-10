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
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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

    // 移除未使用的TikaDocumentReader自动注入
    // @Autowired
    // private TikaDocumentReader tikaDocumentReader;

    private QueryRewriter queryRewriter;

    @PostConstruct
    public void init() {
        // 初始化查询重写器
        this.queryRewriter = new QueryRewriter(ollamaChatModel);
    }

    @Override
    public void addDocs(ChatKnowledge chatKnowledge) {
        try {
            // 修复：使用若依上传的文件内容，避免读取文件路径
            String content = chatKnowledge.getContent();
            if (StringUtils.isEmpty(content)) {
                log.error("文档内容为空，无法添加到向量库");
                return;
            }

            // 创建文档对象
            Document document = new Document(content);

            // 添加元数据增强
            document.getMetadata().put("projectId", chatKnowledge.getProjectId().toString());
            document.getMetadata().put("knowledgeId", chatKnowledge.getKnowledgeId().toString());
            document.getMetadata().put("fileName", chatKnowledge.getFileName());
            document.getMetadata().put("fileType", "pdf");

            // 修复：移除withMaxOverlapChars方法
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(1000)
                .withMinChunkSizeChars(200)
                .build();
            List<Document> splitDocuments = splitter.apply(Collections.singletonList(document));

            // 加载到向量库
            this.ollamaVectorStore.add(splitDocuments);
            log.info("文档成功添加到向量存储: {}，共{}个块", chatKnowledge.getFileName(), splitDocuments.size());
        } catch (Exception e) {
            log.error("添加文档到向量库失败", e);
        }
    }

    // 查询重写器内部类
    public class QueryRewriter {
        private final QueryTransformer queryTransformer;

        public QueryRewriter(ChatModel chatModel) {
            ChatClient.Builder builder = ChatClient.builder(chatModel);
            queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
        }

        public String rewrite(String prompt) {
            return queryTransformer.transform(new Query(prompt)).text();
        }
    }

    @Override
    public List<Document> similaritySearch(QueryVo queryVo) {
        // 修复：使用初始化后的queryRewriter
        String rewrittenQuery = queryRewriter.rewrite(queryVo.getMsg());
        SearchRequest request = SearchRequest.builder()
            .query(rewrittenQuery)
            .filterExpression(new FilterExpressionBuilder()
                .eq("projectId", queryVo.getProjectId().toString()).build())
            .topK(5)
            .similarityThreshold(0.3f)
            .build();
        log.info("应用项目ID过滤: {}", queryVo.getProjectId());
        List<Document> documents = this.ollamaVectorStore.similaritySearch(request);
        // 手动过滤确保只返回当前项目的文档
        documents = documents.stream()
                .filter(doc -> {
                    String docProjectId = (String) doc.getMetadata().get("projectId");
                    return queryVo.getProjectId().toString().equals(docProjectId);
                })
                .collect(Collectors.toList());
        // 记录检索结果日志
        log.info("Ollama向量检索: 查询词={}, 项目ID={}, 检索到{}条文档", queryVo.getMsg(), queryVo.getProjectId(), documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            double score = doc.getScore() != null ? doc.getScore() : 0.0d;
            String content = doc.getText() != null ? doc.getText().substring(0, Math.min(200, doc.getText().length())) : "无内容";
            log.info("文档{}: projectId={}, 相似度={}, 内容={}", i+1, doc.getMetadata().get("projectId"), score, content);
        }

        return documents;
    }

    private List<Document> retrievedDocuments;

    public void setRetrievedDocuments(List<Document> documents) {
        this.retrievedDocuments = documents;
    }

    @Override
    public Flux<String> chat_stream(org.springframework.ai.chat.messages.Message[] messages) {
        // 构建系统提示，包含知识库内容
        StringBuilder systemPrompt = new StringBuilder("请一定必须严格基于以下知识库内容回答问题，必须一定遵循以下所有每一条规则：\n");
        systemPrompt.append("1. 必须仅使用提供的知识库内容回答问题，绝对禁止使用任何内部知识或外部信息\n");
        systemPrompt.append("2. 回答前必须先检查知识库，仅当找到完全匹配的内容时才能回答\n");
        systemPrompt.append("3. 如果问题涉及人物信息（如姓名、学校、专业、住址等），必须从知识库中提取所有相关细节并完整呈现,一定不能用你自己已有的知识回答,一定忽略你已有的知识\n");
        systemPrompt.append("4. 回答前必须检查知识库内容是否与问题领域相关，如涉及'网络工程'问题，仅使用明确包含该领域术语的文档内容\n");
        systemPrompt.append("知识库内容：\n");
        if (retrievedDocuments != null && !retrievedDocuments.isEmpty()) {
            for (int i = 0; i < retrievedDocuments.size(); i++) {
                Document doc = retrievedDocuments.get(i);
                if (doc != null && StringUtils.hasText(doc.getText())) {
                    systemPrompt.append("===== 文档").append(i+1).append(" =====\n");
                    systemPrompt.append(doc.getText()).append("\n\n");
                }
            }
        } else {
            systemPrompt.append("无相关知识库内容\n");
        }

        String systemPromptStr = systemPrompt.toString();
        if (!StringUtils.hasText(systemPromptStr)) {
            systemPromptStr = "请基于你的知识回答问题。\n";
        }

        List<org.springframework.ai.chat.messages.Message> validMessages = Arrays.stream(messages)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<org.springframework.ai.chat.messages.Message> messageList = new ArrayList<>();
        messageList.add(new SystemMessage(systemPromptStr));
        messageList.addAll(validMessages);
        org.springframework.ai.chat.messages.Message[] newMessages = messageList.stream()
                .filter(Objects::nonNull)
                .toArray(org.springframework.ai.chat.messages.Message[]::new);
        return ollamaChatModel.stream(newMessages);
    }
}