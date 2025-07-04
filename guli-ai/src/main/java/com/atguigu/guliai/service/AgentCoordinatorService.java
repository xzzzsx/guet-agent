package com.atguigu.guliai.service;

import com.atguigu.common.utils.StringUtils;
import com.atguigu.guliai.agent.*;
import com.atguigu.guliai.enums.AgentTypeEnum;
import com.atguigu.guliai.pojo.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
public class AgentCoordinatorService {

    private final RouteAgent routeAgent;
    private final RecommendAgent recommendAgent;
    private final ReservationAgent reservationAgent;
    private final SchoolQueryAgent schoolQueryAgent;
    private final MapsQueryAgent mapsQueryAgent;

    @Autowired
    public AgentCoordinatorService(RouteAgent routeAgent,
                                   RecommendAgent recommendAgent,
                                   ReservationAgent reservationAgent,
                                   SchoolQueryAgent schoolQueryAgent,
                                   MapsQueryAgent mapsQueryAgent) {
        this.routeAgent = routeAgent;
        this.recommendAgent = recommendAgent;
        this.reservationAgent = reservationAgent;
        this.schoolQueryAgent = schoolQueryAgent;
        this.mapsQueryAgent = mapsQueryAgent;
    }

    // 使用自定义Message类型
    public Flux<String> coordinate(List<Message> historyMessages, String sessionId, Long projectId) {
        log.info("【智能体路由】开始处理问题，历史消息数: {}", historyMessages.size());

        // 1. 获取当前问题（最后一条用户消息）
        String currentQuestion = extractLastUserMessage(historyMessages);
        if (StringUtils.isEmpty(currentQuestion)) {
            return Flux.error(new RuntimeException("无法获取当前问题"));
        }

        log.info("【智能体路由】当前问题: {}", currentQuestion);

        // 2. 使用RouteAgent进行意图识别
        return routeAgent.processStream(historyMessages, sessionId, projectId)
                .collectList()  // 收集所有响应片段
                .flatMapMany(parts -> {
                    // 合并片段为完整意图字符串
                    String fullIntent = String.join("", parts);
                    log.info("【智能体路由】完整意图: {}", fullIntent);

                    // 2. 转换为AgentType
                    AgentTypeEnum agentType = AgentTypeEnum.agentNameOf(fullIntent.trim());
                    log.info("【智能体路由】解析后的智能体类型: {}", agentType);

                    // 3. 处理非路由类型
                    if (agentType != null && agentType != AgentTypeEnum.ROUTE) {
                        log.info("【智能体路由】分发到 {} 智能体", agentType.getDesc());
                        Agent targetAgent = getAgentByType(agentType);

                        // 调用目标智能体时传入完整历史消息
                        return targetAgent.processStream(historyMessages, sessionId, projectId);
                    }

                    // 4. 非路由结果直接返回
                    log.info("【智能体路由】未识别到特定智能体，直接返回原始响应");

                    // 修复：当返回ROUTE时，调用默认智能体处理
                    return recommendAgent.processStream(historyMessages, sessionId, projectId)
                            .map(response -> {
                                // 移除可能的智能体标签前缀
                                return removeAgentTags(response);
                            });
                });
    }

    // 新增：移除响应中的智能体标签
    private String removeAgentTags(String response) {
        // 移除所有可能的智能体标签前缀
        for (AgentTypeEnum agentType : AgentTypeEnum.values()) {
            String tag = agentType.name() + "\n";
            if (response.startsWith(tag)) {
                return response.substring(tag.length());
            }
        }
        return response;
    }

    private String extractLastUserMessage(List<Message> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return "";
        }
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            Message msg = historyMessages.get(i);
            if (msg.getType() == 0) { // 0 表示用户消息
                return msg.getContent();
            }
        }
        return "";
    }

    private Agent getAgentByType(AgentTypeEnum agentType) {
        switch (agentType) {
            case RECOMMEND:
                return recommendAgent;
            case RESERVATION:
                return reservationAgent;
            case SCHOOL_QUERY:
                return schoolQueryAgent;
            case MAPS_QUERY:
                return mapsQueryAgent;
            default:
                return recommendAgent; // 默认返回推荐智能体
        }
    }
}