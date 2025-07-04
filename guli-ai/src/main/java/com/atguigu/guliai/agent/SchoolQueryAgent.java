// SchoolQueryAgent.java
package com.atguigu.guliai.agent;

import com.atguigu.guliai.config.AiAdvisorConfig;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.enums.AgentTypeEnum;
import com.atguigu.guliai.pojo.Message;
import com.atguigu.guliai.tools.SchoolQueryTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SchoolQueryAgent extends AbstractAgent {

    private final SchoolQueryTools schoolQueryTools;
    private final AiAdvisorConfig.ServiceChatClient serviceChatClient;

    @Autowired
    public SchoolQueryAgent(SchoolQueryTools schoolQueryTools, AiAdvisorConfig.ServiceChatClient serviceChatClient) {
        this.schoolQueryTools = schoolQueryTools;
        this.serviceChatClient = serviceChatClient;
    }

    @Override
    public Flux<String> processStream(List<Message> historyMessages, String sessionId, Long projectId) {
        // 获取最近3条用户消息并合并为上下文
        String context = getRecentUserMessages(historyMessages).stream()
                .collect(Collectors.joining("\n"));

        // 使用校区查询智能体的提示词
        return serviceChatClient.prompt()
                .system(s -> s.text(SystemConstant.SCHOOL_QUERY_AGENT_PROMPT))
                .user(context)  // 使用合并后的上下文
                .stream()
                .content();
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.SCHOOL_QUERY;
    }

    @Override
    public Object[] tools() {
        return new Object[]{schoolQueryTools};
    }
}