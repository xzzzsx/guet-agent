package com.atguigu.guliai.agent;

import com.atguigu.guliai.enums.AgentTypeEnum;
import com.atguigu.guliai.pojo.Message; // 使用自定义Message类型
import reactor.core.publisher.Flux;

import java.util.List;

public interface Agent {
    // 统一使用自定义Message类型
    Flux<String> processStream(List<Message> historyMessages, String sessionId, Long projectId);
    AgentTypeEnum getAgentType();
}