package com.atguigu.guliai.agent;

import com.atguigu.guliai.config.AiAdvisorConfig;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.enums.AgentTypeEnum;
import com.atguigu.guliai.tools.CourseQueryTools;
import com.atguigu.guliai.tools.DatabaseQueryTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class RecommendAgent extends AbstractAgent {

    private final CourseQueryTools courseQueryTools;
    private final DatabaseQueryTools databaseQueryTools;
    private final AiAdvisorConfig.ServiceChatClient serviceChatClient; // 新增

    @Autowired
    public RecommendAgent(CourseQueryTools courseQueryTools, DatabaseQueryTools databaseQueryTools, AiAdvisorConfig.ServiceChatClient serviceChatClient) {
        this.courseQueryTools = courseQueryTools;
        this.databaseQueryTools = databaseQueryTools;
        this.serviceChatClient = serviceChatClient;
    }

    @Override
    public Flux<String> processStream(String question, String sessionId, Long projectId) {
        // 使用推荐智能体的提示词和工具
        return serviceChatClient.prompt()
                .system(s -> s.text(SystemConstant.RECOMMEND_AGENT_PROMPT))
                .user(question)
                .stream()
                .content();
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.RECOMMEND;
    }

    @Override
    public Object[] tools() {
        return new Object[]{courseQueryTools, databaseQueryTools};
    }
}