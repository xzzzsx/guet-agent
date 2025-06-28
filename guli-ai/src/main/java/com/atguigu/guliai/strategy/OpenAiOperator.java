package com.atguigu.guliai.strategy;

import com.atguigu.common.utils.StringUtils;
import com.atguigu.guliai.config.AiAdvisorConfig;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.vo.QueryVo;
import com.atguigu.system.domain.ChatKnowledge;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * openai的具体策略类
 */
@AiBean(SystemConstant.MODEL_TYPE_OPENAI)
public class OpenAiOperator implements AiOperator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiOperator.class);

    @Autowired
    private QdrantVectorStore openAiVectorStore;

    @Autowired
    private OpenAiChatModel openAiChatModel;

    @Autowired
    private ChatClient chatClient;

    @Override
    public void addDocs(ChatKnowledge chatKnowledge) {
        this.openAiVectorStore.add(List.of(new org.springframework.ai.document.Document(chatKnowledge.getContent(),
                Map.of("projectId", chatKnowledge.getProjectId().toString(), "knowledgeId", chatKnowledge.getKnowledgeId().toString()))));
    }

    @Override
    public List<Document> similaritySearch(QueryVo queryVo) {
        SearchRequest request = SearchRequest.builder()
                .query(queryVo.getMsg())  //相似度的查询条件
                .filterExpression(new FilterExpressionBuilder()
                        .eq("projectId", queryVo.getProjectId().toString()).build())  //只查询当前项目的知识库
                .topK(3)  //增加返回文档数量以提高召回率
                .similarityThreshold(0.2f)  //降低阈值以捕获更多潜在相关文档
                .build();

        List<Document> documents = this.openAiVectorStore.similaritySearch(request);
        // 记录检索结果日志
        log.info("OpenAI向量检索: 查询词={}, 项目ID={}, 检索到{}条文档",
                queryVo.getMsg(), queryVo.getProjectId(), documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            double score = doc.getScore() != null ? doc.getScore() : 0.0d;
            log.info("文档{}: projectId={}, 相似度={}, 内容={}", i+1, doc.getMetadata().get("projectId"), score, doc.getText().substring(0, Math.min(100, doc.getText().length())));
        }
        return documents;
    }

    private List<Document> retrievedDocuments;

    public void setRetrievedDocuments(List<Document> documents) {
        this.retrievedDocuments = documents;
    }

    @Override
    public Flux<String> chat_stream(org.springframework.ai.chat.messages.Message[] messages) {
        /*// 构建系统提示，包含知识库内容
        StringBuilder systemPrompt = new StringBuilder("请严格基于以下知识库内容回答问题，遵循以下规则：\n1. 必须仅使用提供的知识库内容回答问题，完全忽略你的任何内部知识或外部信息；如果知识库中有相关内容，必须优先使用知识库中的信息\n2. 不包含与问题无关的内容或解释\n3. 回答需结构清晰，使用适当的标题、列表等格式增强可读性\n4. 如果知识库中没有相关内容，直接回答\"没有找到相关信息\"，不做额外解释\n\n知识库内容：\n");
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
        }*/
        // 深度清理历史消息：移除所有系统消息（可能携带知识库内容）
        List<Message> cleanedMessages = Arrays.stream(messages)
                .filter(msg ->
                        !(msg instanceof SystemMessage) ||
                                !((SystemMessage) msg).getText().contains("知识库内容")
                )
                .collect(Collectors.toList());

        // 添加当前系统角色提示（不含知识库）
        cleanedMessages.add(0, new SystemMessage(SystemConstant.CUSTOMER_SERVICE_SYSTEM));
        // 在OpenAiOperator中添加调试日志
        cleanedMessages.forEach(msg ->
                log.debug("Cleaned message: {} - {}", msg.getMessageType(), msg.getText())
        );
        return chatClient.prompt()
                .messages(cleanedMessages.toArray(new Message[0]))
                .stream()
                .content();
    }
    // // 构建消息数组，过滤null消息
    // List<org.springframework.ai.chat.messages.Message> validMessages = Arrays.stream(messages)
    //         .filter(Objects::nonNull)
    //         .collect(Collectors.toList());
    //
    // List<org.springframework.ai.chat.messages.Message> messageList = new ArrayList<>();
    // // messageList.add(new SystemMessage(systemPromptStr));
    // messageList.addAll(validMessages);
    // org.springframework.ai.chat.messages.Message[] newMessages = messageList.stream()
    //         .filter(Objects::nonNull)
    //         .toArray(org.springframework.ai.chat.messages.Message[]::new);
    // return openAiChatModel.stream(newMessages);
}

