package com.atguigu.guliai.service;

import com.atguigu.guliai.agent.Agent;
import com.atguigu.guliai.enums.AgentTypeEnum;
import com.atguigu.guliai.vo.QueryVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
public class AgentCoordinatorService {

    private final Map<String, Agent> agents;
    @Lazy // 添加延迟加载注解解决循环依赖
    private AiService aiService; // 从构造器注入改为字段注入

    @Autowired
    public AgentCoordinatorService(ApplicationContext context, AiService aiService) {
        this.agents = context.getBeansOfType(Agent.class);
        this.aiService = aiService;

        // 注册检查
        log.info("===== 智能体注册检查 =====");
        for (AgentTypeEnum type : AgentTypeEnum.values()) {
            try {
                Agent agent = getAgentByType(type);
                log.info("✅ 智能体注册成功: {} => {}", type, agent.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("❌❌❌❌ 智能体注册失败: {}", type, e);
            }
        }
    }

    public Flux<String> coordinate(String question, String sessionId, Long projectId) {
        log.info("【智能体路由】开始处理问题: {}", question);

        // 1. 使用RouteAgent进行意图识别
        Agent routeAgent = getAgentByType(AgentTypeEnum.ROUTE);

        // 修改点：收集所有响应片段并合并为完整字符串
        return routeAgent.processStream(question, sessionId, projectId)
                .collectList()  // 收集所有响应片段
                .flatMapMany(parts -> {
                    // 合并片段为完整意图字符串
                    String fullIntent = String.join("", parts);
                    log.info("【智能体路由】完整意图: {}", fullIntent);

                    // 2. 转换为AgentType
                    AgentTypeEnum agentType = AgentTypeEnum.agentNameOf(fullIntent.trim());

                    // 3. 处理非路由类型
                    if (agentType != null && agentType != AgentTypeEnum.ROUTE) {
                        log.info("【智能体路由】分发到 {} 智能体", agentType.getDesc());
                        Agent targetAgent = getAgentByType(agentType);

                        // 直接调用目标智能体处理
                        return targetAgent.processStream(question, sessionId, projectId);
                    }

                    // 4. 非路由结果直接返回
                    return Flux.just(fullIntent);
                });
    }

    private Agent getAgentByType(AgentTypeEnum type) {
        return agents.values().stream()
                .filter(agent -> agent.getAgentType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "未找到类型为 " + type + " 的智能体实现。已注册的智能体: " + agents.keySet()
                ));
    }
}