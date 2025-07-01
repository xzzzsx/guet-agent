package com.atguigu.guliai.config;

import com.atguigu.guliai.advisor.RecordOptimizationAdvisor;
import com.atguigu.guliai.service.AiService;
import com.atguigu.guliai.tools.CourseQueryTools;
import com.atguigu.guliai.tools.CourseTools;
import com.atguigu.guliai.tools.DatabaseQueryTools;
import com.atguigu.guliai.tools.ReservationTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import com.atguigu.guliai.constant.SystemConstant;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class AiAdvisorConfig {

    @Primary
    @Bean
    public ChatModel primaryChatModel(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        return chatModel;
    }

    @Bean
    public Advisor recordOptimizationAdvisor() {
        return new RecordOptimizationAdvisor(); // 不再需要AiService参数
    }

    @Bean
    public ChatClient serviceChatClient(OpenAiChatModel openAiChatModel,
                                        Advisor simpleLoggerAdvisor,
                                        Advisor safeGuardAdvisor,
                                        Advisor recordOptimizationAdvisor) {

        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(
                        simpleLoggerAdvisor,           // 日志记录
                        safeGuardAdvisor,              // 安全防护
                        recordOptimizationAdvisor      // 记录优化
                )
                .build();
    }

    @Bean
    public Advisor simpleLoggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    @Bean
    public List<String> sensitiveWords() {
        return List.of("敏感词1", "敏感词2");
    }

    @Bean
    public Advisor safeGuardAdvisor(List<String> sensitiveWords) {
        return new SafeGuardAdvisor(
                sensitiveWords,
                "敏感词提示：请勿输入敏感词！",
                Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER
        );
    }
}