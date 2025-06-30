package com.atguigu.guliai.agent;

import com.atguigu.guliai.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ReservationAgent extends AbstractAgent {
    @Override
    public Flux<String> processStream(String question, String sessionId, Long projectId) {
        // 直接使用传入的 projectId
        return baseChatStream(question, projectId, sessionId);
        // 重要修复：移除前缀添加逻辑
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.RESERVATION;
    }
}