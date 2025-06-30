package com.atguigu.guliai.agent;

import com.atguigu.guliai.enums.AgentTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class RouteAgent extends AbstractAgent {
    private static final String SYSTEM_PROMPT = """
        你是一个智能路由助手，负责分析用户意图并分类：
        1. 当问题涉及课程查询、推荐时返回：RECOMMEND
        2. 当问题涉及预约、报名时返回：RESERVATION
        3. 其他问题直接回答
        
        示例：
        用户：有哪些编程课程？ -> RECOMMEND
        用户：我想预约Java课程 -> RESERVATION
        用户：你好 -> 你好！有什么可以帮您？
        """;

    @Override
    public Flux<String> processStream(String question, String sessionId, Long projectId) {
        // 创建包含系统提示的问题
        String fullQuestion = SYSTEM_PROMPT + "\n用户问题：" + question;

        // 简单路由逻辑
        if (question.contains("课程") || question.contains("推荐")) {
            return Flux.just("RECOMMEND");
        } else if (question.contains("预约") || question.contains("报名")) {
            return Flux.just("RESERVATION");
        }
        // 修复：传递null作为projectId（因为路由层不需要具体项目）
        return baseChatStream(fullQuestion, projectId, sessionId);
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.ROUTE;
    }
}