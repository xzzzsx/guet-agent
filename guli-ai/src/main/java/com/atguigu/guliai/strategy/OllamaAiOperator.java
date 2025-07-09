package com.atguigu.guliai.strategy;

import com.atguigu.common.utils.StringUtils;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.vo.QueryVo;
import com.atguigu.system.domain.ChatKnowledge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.ollama.OllamaChatModel;
import java.io.File;
import java.io.FileInputStream;
// 删除未使用的错误导入
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
// import org.springframework.ai.transformer.splitter.RecursiveCharacterTextSplitter; // 删除此行
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ollama的具体策略类
 */
@AiBean(SystemConstant.MODEL_TYPE_OLLAMA)
public class OllamaAiOperator implements AiOperator {

    @Autowired
    private QdrantVectorStore ollamaVectorStore;

    @Autowired
    private OllamaChatModel ollamaChatModel;

    @Override
    public void addDocs(ChatKnowledge chatKnowledge) {
        String content = chatKnowledge.getContent();
        if (StringUtils.isEmpty(content)) {
            log.error("文档内容为空，无法添加知识库: {}", chatKnowledge.getFileName());
            throw new IllegalArgumentException("上传文件内容解析失败，请检查文件格式");
        }
        
        try {
            // 创建文档对象
            Document document = new Document(content, 
                Map.of("projectId", chatKnowledge.getProjectId().toString(), 
                       "knowledgeId", chatKnowledge.getKnowledgeId().toString()));
            
            // 使用TokenTextSplitter进行文档分块（适配当前版本API）
            // 将分块大小从1000增加到2000
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(2000)
                .withMinChunkSizeChars(200)
                .build();
            List<Document> splitDocuments = splitter.apply(List.of(document));
            
            // 添加向量存储操作
            this.ollamaVectorStore.add(splitDocuments);
            log.info("文档成功添加到向量存储: {}，共{}个块", chatKnowledge.getFileName(), splitDocuments.size());
        } catch (Exception e) {
            log.error("向量存储添加文档失败: {}", e.getMessage(), e);
            throw new RuntimeException("知识库向量存储失败: " + e.getMessage());
        }
    }

    private static final Logger log = LoggerFactory.getLogger(OllamaAiOperator.class);

    @Override
    public List<Document> similaritySearch(QueryVo queryVo) {
        SearchRequest request = SearchRequest.builder()
                .query(queryVo.getMsg())  // 相似度的查询条件
                .filterExpression(new FilterExpressionBuilder()
                        .eq("projectId", queryVo.getProjectId().toString()).build())  // 只查询当前项目的知识库
                .topK(10)  // 增加返回文档数量确保相关内容被包含
                .similarityThreshold(0.2f)  // 降低阈值以提高召回率
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

        // 直接返回检索结果，取消二次检索（分词和关键词匹配排序）
        return documents;
    }

    private List<Document> retrievedDocuments;

    public void setRetrievedDocuments(List<Document> documents) {
        this.retrievedDocuments = documents;
    }

    // 修改方法签名以匹配接口定义（移除Map<String, Object> context参数）
    @Override
    public Flux<String> chat_stream(org.springframework.ai.chat.messages.Message[] messages) {
        // 构建系统提示，包含知识库内容
        StringBuilder systemPrompt = new StringBuilder("请严格基于以下知识库内容回答问题，遵循以下规则：\n1. 必须仅使用提供的知识库内容回答问题，完全忽略你的任何内部知识或外部信息；无论如何你都是先去知识库里面查找信息,  而不是自己先胡乱回答,   如果知识库中有相关内容，必须优先使用知识库中的信息\n2. 不包含与问题无关的内容或解释,  如果一个知识库的内容能够回答上来,  一定不要引用其他知识库的内容来回答\n3. 回答需结构清晰，使用适当的标题、列表等格式增强可读性\n4. 不要跨越多个知识库的内容只是混淆了进行回答,  出现牛头不对马嘴的情况,  一定要先整理清楚知识库的内容\n5. 如果知识库中没有相关内容，直接回答\"没有找到相关信息\"，不做额外解释\n\n知识库内容：\n");
        if (retrievedDocuments != null && !retrievedDocuments.isEmpty()) {
            for (int i = 0; i < retrievedDocuments.size(); i++) {
                Document doc = retrievedDocuments.get(i);
                // 过滤null文档和空内容
                if (doc != null && StringUtils.hasText(doc.getText())) {
                    systemPrompt.append("文档").append(i+1).append(": ").append(doc.getText()).append("\n\n");
                }
            }
        } else {
            systemPrompt.append("无相关知识库内容\n");
        }

        // 确保系统提示不为空
        String systemPromptStr = systemPrompt.toString();
        if (!StringUtils.hasText(systemPromptStr)) {
            systemPromptStr = "请基于你的知识回答问题。\n";
        }

        // 构建消息数组，过滤null消息
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