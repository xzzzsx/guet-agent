package com.atguigu.guliai.agent;

import com.atguigu.guliai.config.AiAdvisorConfig;
import com.atguigu.guliai.constant.SystemConstant;
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
    private final AiAdvisorConfig.ServiceChatClient serviceChatClient; // 新增

    @Autowired
    public ReservationAgent(ReservationTools reservationTools, DatabaseQueryTools databaseQueryTools, AiAdvisorConfig.ServiceChatClient serviceChatClient) {
        this.reservationTools = reservationTools;
        this.databaseQueryTools = databaseQueryTools;
        this.serviceChatClient = serviceChatClient;
    }

    @Override
    public Flux<String> processStream(String question, String sessionId, Long projectId) {
        // 使用预约智能体的提示词和工具
        return serviceChatClient.prompt()
                .system(s -> s.text(SystemConstant.RESERVATION_AGENT_PROMPT))
                .user(question)
                .stream()
                .content();
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.RESERVATION;
    }

    @Override
    public Object[] tools() {
        return new Object[]{reservationTools, databaseQueryTools};
    }
}