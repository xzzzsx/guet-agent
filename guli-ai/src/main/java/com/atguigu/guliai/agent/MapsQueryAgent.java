package com.atguigu.guliai.agent;

import com.atguigu.guliai.config.AiAdvisorConfig;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.enums.AgentTypeEnum;
import com.atguigu.guliai.tools.AmapTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class MapsQueryAgent extends AbstractAgent {

    private final AmapTools amapTools;
    private final AiAdvisorConfig.ServiceChatClient serviceChatClient;

    @Autowired
    public MapsQueryAgent(AmapTools amapTools,
                          AiAdvisorConfig.ServiceChatClient serviceChatClient) {
        this.amapTools = amapTools;
        this.serviceChatClient = serviceChatClient;
    }

    @Override
    public Flux<String> processStream(String question, String sessionId, Long projectId) {
        return serviceChatClient.prompt()
                .system(s -> s.text(SystemConstant.MAPS_QUERY_AGENT_PROMPT)) // 使用更新后的提示词
                .user(question)
                .stream()
                .content();
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MAPS_QUERY;
    }

    @Override
    public Object[] tools() {
        // 返回全部地图工具（包含新增的未来天气和IP定位工具）
        return new Object[]{amapTools};
    }
}