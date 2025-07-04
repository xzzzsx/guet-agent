// RouteAgent.java
package com.atguigu.guliai.agent;

import com.atguigu.guliai.config.AiAdvisorConfig;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.enums.AgentTypeEnum;
import com.atguigu.guliai.pojo.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class RouteAgent extends AbstractAgent {

    private final AiAdvisorConfig.ServiceChatClient serviceChatClient;

    @Autowired
    public RouteAgent(AiAdvisorConfig.ServiceChatClient serviceChatClient) {
        this.serviceChatClient = serviceChatClient;
    }

    @Override
    public Flux<String> processStream(List<Message> historyMessages, String sessionId, Long projectId) {
        // 路由智能体保持只使用最后一条消息（设计决策）
        // 获取最近3条用户消息
        List<String> recentUserMessages = getRecentUserMessages(historyMessages);
        // 当前问题（最后一条用户消息）
        String currentQuestion = recentUserMessages.isEmpty() ? "" : recentUserMessages.get(0);

        // 增强路由提示词：强制要求返回智能体名称
        String routePrompt = SystemConstant.ROUTE_AGENT_PROMPT +
                "\n请根据用户问题直接返回智能体名称：" +
                "\n- 如果用户需要查找校区信息，返回: SCHOOL_QUERY" +
                "\n- 如果用户需要查询地图、路线或距离，返回: MAPS_QUERY" +
                "\n- 如果用户需要预约服务，返回: RESERVATION" +
                "\n- 如果用户需要课程推荐，返回: RECOMMEND" +
                "\n- 如果以上都不适用，返回: ROUTE" +
                "\n不要解释原因，直接返回智能体名称！";

        // 使用路由智能体的提示词
        return serviceChatClient.prompt()
                .system(s -> s.text(routePrompt))
                .user(currentQuestion)
                .stream()
                .content();
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.ROUTE;
    }

    @Override
    public Object[] tools() {
        return new Object[0];
    }
}