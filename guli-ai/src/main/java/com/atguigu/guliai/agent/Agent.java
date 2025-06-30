package com.atguigu.guliai.agent;

import com.atguigu.guliai.enums.AgentTypeEnum;
import reactor.core.publisher.Flux;

public interface Agent {
    Flux<String> processStream(String question, String sessionId,Long projectId);
    AgentTypeEnum getAgentType();
}