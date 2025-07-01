package com.atguigu.guliai.agent;

import com.atguigu.guliai.config.AiAdvisorConfig;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.enums.AgentTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class RouteAgent extends AbstractAgent {

    @Autowired
    private AiAdvisorConfig.ServiceChatClient serviceChatClient; // 使用专用ChatClient

    @Override
    public Flux<String> processStream(String question, String sessionId, Long projectId) {
        // 构建路由请求
        return serviceChatClient.prompt()
                .system(s -> s.text(SystemConstant.ROUTE_AGENT_PROMPT))
                .user(question)
                .stream()
                .content();
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.ROUTE;
    }

    @Override
    public Object[] tools() {
        return new Object[0]; // 路由智能体不需要工具
    }
}