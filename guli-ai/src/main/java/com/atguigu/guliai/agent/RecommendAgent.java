package com.atguigu.guliai.agent;

import com.atguigu.guliai.config.AiAdvisorConfig;
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

    @Autowired
    public RecommendAgent(CourseQueryTools courseQueryTools, DatabaseQueryTools databaseQueryTools) {
        this.courseQueryTools = courseQueryTools;
        this.databaseQueryTools = databaseQueryTools;
    }

    @Override
    public Flux<String> processStream(String question, String sessionId, Long projectId) {
        return baseChatStream(question, projectId, sessionId);
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.RECOMMEND;
    }

    // 推荐智能体需要课程查询和数据库查询工具
    @Override
    public Object[] tools() {
        return new Object[]{courseQueryTools, databaseQueryTools};
    }
}