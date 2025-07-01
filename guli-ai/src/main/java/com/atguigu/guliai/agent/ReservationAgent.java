package com.atguigu.guliai.agent;

import com.atguigu.guliai.config.AiAdvisorConfig;
import com.atguigu.guliai.enums.AgentTypeEnum;
import com.atguigu.guliai.tools.DatabaseQueryTools;
import com.atguigu.guliai.tools.ReservationTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ReservationAgent extends AbstractAgent {

    private final ReservationTools reservationTools;
    private final DatabaseQueryTools databaseQueryTools;

    @Autowired
    public ReservationAgent(ReservationTools reservationTools, DatabaseQueryTools databaseQueryTools) {
        this.reservationTools = reservationTools;
        this.databaseQueryTools = databaseQueryTools;
    }

    @Override
    public Flux<String> processStream(String question, String sessionId, Long projectId) {
        return baseChatStream(question, projectId, sessionId);
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.RESERVATION;
    }

    // 预约智能体需要预约和数据库查询工具
    @Override
    public Object[] tools() {
        return new Object[]{reservationTools, databaseQueryTools};
    }
}