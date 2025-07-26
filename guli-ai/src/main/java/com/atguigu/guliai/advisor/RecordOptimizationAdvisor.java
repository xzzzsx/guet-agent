package com.atguigu.guliai.advisor;

import cn.hutool.core.convert.Convert;
import com.atguigu.guliai.service.AiService;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import reactor.core.publisher.Flux;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component; // 新增注解

@Component // 确保被Spring管理
public class RecordOptimizationAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    // 新增自定义会话ID键常量
    public static final String CUSTOM_SESSION_ID_KEY = "guliai.sessionId";

    @Autowired
    private ApplicationContext applicationContext;

    private AiService getAiService() {
        return applicationContext.getBean(AiService.class);
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);
        ChatResponse response = advisedResponse.response();

        String text = response.getResult().getOutput().getText();

        if (isRoutingTag(text)) {
            // 使用自定义会话ID键
            Long sessionId = Convert.toLong(advisedResponse.adviseContext().get(CUSTOM_SESSION_ID_KEY));

            if (sessionId != null) {
                // 发布事件而不是直接调用
                applicationContext.publishEvent(new DeleteMessagesEvent(this, sessionId));
            }
        }
        return advisedResponse;
    }

    public class DeleteMessagesEvent extends ApplicationEvent {
        private final Long sessionId;

        public DeleteMessagesEvent(Object source, Long sessionId) {
            super(source);
            this.sessionId = sessionId;
        }

        public Long getSessionId() {
            return sessionId;
        }
    }

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        return chain.nextAroundStream(advisedRequest);
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 100;
    }

    private boolean isRoutingTag(String text) {
        return "RECOMMEND".equals(text) || "RESERVATION".equals(text) || "SCHOOL_QUERY".equals(text) || "MAPS_QUERY".equals(text);
    }
}