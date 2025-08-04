package com.atguigu.guliai.agent;

import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.enums.AgentTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class MapsQueryAgent extends AbstractAgent {
    private final ChatClient chatClient;

    @Override
    @Retryable(value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000, multiplier = 2))
    public Flux<String> processStream(String question, String sessionId, Long projectId) {
        log.info("MapsQueryAgent处理问题: {}", question);

        return chatClient.prompt()
                .system(s -> s.text(SystemConstant.MAPS_QUERY_AGENT_PROMPT))
                .user(question)
                .stream()
                .content()
                .timeout(Duration.ofSeconds(90))  // 增加超时时间到90秒
                .onErrorResume(throwable -> {
                    log.warn("MCP工具调用失败，尝试返回默认响应: {}", throwable.getMessage());

                    // 检查是否是连接问题
                    if (throwable.getMessage() != null &&
                            (throwable.getMessage().contains("Connection reset") ||
                                    throwable.getMessage().contains("timeout") ||
                                    throwable.getMessage().contains("Did not observe"))) {

                        log.info("检测到连接问题，建议用户稍后重试");
                        return Flux.just("抱歉，地图服务连接暂时不稳定，请稍后重试。这通常是网络波动导致的，很快就会恢复。");
                    }

                    return Flux.just("抱歉，地图服务暂时不可用，请稍后再试。您可以尝试提供更具体的位置信息。");
                });
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MAPS_QUERY;
    }

    @Override
    public Object[] tools() {
        return new Object[]{};
    }
}