package com.atguigu.guliai.strategy;

import com.atguigu.common.utils.StringUtils;
import com.atguigu.guliai.config.AiAdvisorConfig;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.vo.QueryVo;
import com.atguigu.system.domain.ChatKnowledge;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

    @Autowired
    public OpenAiOperator(
            @Lazy ChatClient chatClient, // 关键修改：使用@Lazy延迟注入
            QdrantVectorStore openAiVectorStore,
            OpenAiChatModel openAiChatModel
    ) {
        this.chatClient = chatClient;
        this.openAiVectorStore = openAiVectorStore;
        this.openAiChatModel = openAiChatModel;
    }

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
    public Flux<String> chat_stream(org.springframework.ai.chat.messages.Message[] messages) {
        // 只保留最新的用户消息
        Optional<Message> latestUserMessage = Arrays.stream(messages)
                .filter(msg -> msg.getMessageType() == MessageType.USER)
                .reduce((first, second) -> second); // 获取最后一个用户消息

        List<Message> cleanedMessages = new ArrayList<>();

        // 添加路由智能体提示词
        cleanedMessages.add(0, new SystemMessage(SystemConstant.ROUTE_AGENT_PROMPT));

        // 添加最新的用户消息（如果存在）
        latestUserMessage.ifPresent(cleanedMessages::add);

        // 调试日志
        if (log.isDebugEnabled()) {
            cleanedMessages.forEach(msg ->
                    log.debug("Final message: {} - {}", msg.getMessageType(),
                            msg.getText().substring(0, Math.min(100, msg.getText().length())))
            );
        }

        return chatClient.prompt()
                .messages(cleanedMessages.toArray(new Message[0]))
                .stream()
                .content();
    }
}

