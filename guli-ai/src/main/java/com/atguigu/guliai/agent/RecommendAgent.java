package com.atguigu.guliai.agent;

import com.atguigu.guliai.enums.AgentTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class RecommendAgent extends AbstractAgent {
    @Override
    public Flux<String> processStream(String question, String sessionId, Long projectId) {
        log.info("【验证】当前使用推荐智能体处理问题");
        // 直接使用传入的 projectId
        return baseChatStream(question, projectId, sessionId);
        // 重要修复：移除前缀添加逻辑
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.RECOMMEND;
    }
}