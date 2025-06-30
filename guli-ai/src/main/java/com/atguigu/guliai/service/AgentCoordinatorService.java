package com.atguigu.guliai.service;

import com.atguigu.guliai.agent.Agent;
import com.atguigu.guliai.enums.AgentTypeEnum;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Map;

@Slf4j
@Service
public class AgentCoordinatorService implements SmartLifecycle {
    private final ApplicationContext context;
    private Map<String, Agent> agents;
    private boolean isRunning = false;

    // 确保在所有Bean初始化完成后执行
    @Override
    public void start() {
        if (!isRunning) {
            initAgents();
            isRunning = true;
        }
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    // 最高优先级，确保最后执行
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void initAgents() {
        log.info("===== 智能体延迟初始化 =====");
        this.agents = context.getBeansOfType(Agent.class);
        log.info("智能体注册检查 - 已注册agents: {}", agents.keySet());

        // 详细输出每个Agent的状态
        agents.forEach((name, agent) -> {
            AgentTypeEnum type = agent.getAgentType();
            log.info("智能体[{}]: class={}, type={}", name, agent.getClass().getSimpleName(), type);
        });

        Arrays.stream(AgentTypeEnum.values()).forEach(type -> {
            try {
                Agent agent = getAgentByType(type);
                log.info("✅ 智能体注册成功: {} => {}", type, agent.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("❌ 智能体注册检查失败: {}", type, e);
            }
        });
    }

    @Autowired
    public AgentCoordinatorService(ApplicationContext context) {
        this.context = context;
    }

    public Flux<String> coordinate(String question, String sessionId, Long projectId) { // 新增 projectId 参数
        log.info("【智能体路由】开始处理问题: {}", question);

        Agent routeAgent = getAgentByType(AgentTypeEnum.ROUTE);
        return routeAgent.processStream(question, sessionId, projectId) // 传递 projectId
                .doOnNext(intent -> log.info("【智能体路由】识别到意图: {}", intent))
                .flatMap(intent -> {
                    AgentTypeEnum agentType = AgentTypeEnum.agentNameOf(intent);

                    if (agentType != null && agentType != AgentTypeEnum.ROUTE) {
                        log.info("【智能体路由】分发到 {} 智能体", agentType.getDesc());
                        Agent targetAgent = getAgentByType(agentType);
                        return targetAgent.processStream(question, sessionId, projectId) // 传递 projectId
                                .doOnSubscribe(s -> log.info("【{}智能体】开始处理问题", agentType.getDesc()));
                    }

                    // 重要：非路由结果直接返回，不再重新进入路由系统
                    return Flux.just(intent);
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