package com.atguigu.guliai.agent;

import com.atguigu.guliai.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class RouteAgent extends AbstractAgent {
    @Override
    public Flux<String> processStream(String question, String sessionId, Long projectId) {
        // 路由逻辑
        return baseChatStream(question, projectId, sessionId);
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.ROUTE;
    }

    @Override
    public Object[] tools() {
        // 路由智能体不需要特殊工具
        return new Object[0];
    }

    // 路由智能体不需要特殊工具
}