package com.atguigu.guliai.config;

import com.atguigu.guliai.advisor.RecordOptimizationAdvisor;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.tools.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Function;

@Configuration
public class AiAdvisorConfig {

    // ServiceChatClient interface
    public interface ServiceChatClient {
        PromptSpec prompt();
    }

    // Builder interfaces
    public interface PromptSpec {
        PromptSpec system(Function<SystemSpec, SystemSpec> fn);
        PromptSpec user(String message);
        StreamSpec stream();
    }

    public interface SystemSpec {
        SystemSpec text(String text);
        String getText(); // Add getter method
    }

    public interface StreamSpec {
        Flux<String> content();
    }

    // 添加 ServiceChatClient 实现类
    public class ServiceChatClientImpl implements ServiceChatClient {
        private final ChatClient chatClient;

        public ServiceChatClientImpl(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        @Override
        public PromptSpec prompt() {
            return new PromptSpecImpl(chatClient);
        }

        // 内部实现类
        private class PromptSpecImpl implements PromptSpec {
            private final ChatClient chatClient;
            private String systemText;
            private String userText;

            public PromptSpecImpl(ChatClient chatClient) {
                this.chatClient = chatClient;
            }

            @Override
            public PromptSpec system(Function<SystemSpec, SystemSpec> fn) {
                SystemSpec result = fn.apply(new SystemSpecImpl());
                this.systemText = result.getText(); // Use getter instead of .text
                return this;
            }

            @Override
            public PromptSpec user(String message) {
                this.userText = message;
                return this;
            }

            @Override
            public StreamSpec stream() {
                return new StreamSpecImpl(chatClient, systemText, userText);
            }
        }

        // SystemSpec 实现
        private class SystemSpecImpl implements SystemSpec {
            private String text;

            @Override
            public SystemSpec text(String text) {
                this.text = text;
                return this;
            }

            @Override
            public String getText() { // Implement getter
                return text;
            }
        }

        // StreamSpec 实现
        private class StreamSpecImpl implements StreamSpec {
            private final ChatClient chatClient;
            private final String systemText;
            private final String userText;

            public StreamSpecImpl(ChatClient chatClient, String systemText, String userText) {
                this.chatClient = chatClient;
                this.systemText = systemText;
                this.userText = userText;
            }

            @Override
            public Flux<String> content() {
                return chatClient.prompt()
                        .system(s -> s.text(systemText))
                        .user(userText)
                        .stream()
                        .content();
            }
        }
    }

    @Primary
    @Bean
    public ChatModel primaryChatModel(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        return chatModel;
    }

    @Bean
    public Advisor recordOptimizationAdvisor() {
        return new RecordOptimizationAdvisor(); // 不再需要AiService参数
    }

    // 添加 ServiceChatClient bean
    @Bean
    public ServiceChatClient serviceChatClient(ChatClient chatClient) {
        return new ServiceChatClientImpl(chatClient);
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel,
                                 Advisor simpleLoggerAdvisor,
                                 Advisor safeGuardAdvisor,
                                 Advisor recordOptimizationAdvisor,
                                 CourseQueryTools courseQueryTools,      // 注入工具
                                 DatabaseQueryTools databaseQueryTools, // 注入工具
                                 ReservationTools reservationTools,
                                 SchoolQueryTools schoolQueryTools,
                                 AmapTools amapTools) {   // 注入工具

        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(
                        simpleLoggerAdvisor,
                        safeGuardAdvisor,
                        recordOptimizationAdvisor
                )
                // 注册所有可能用到的工具
                .defaultTools(courseQueryTools, databaseQueryTools, reservationTools,schoolQueryTools,amapTools)
                .build();
    }

    @Bean
    public Advisor simpleLoggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    @Bean
    public List<String> sensitiveWords() {
        return List.of("敏感词", "学生个人信息", "成绩排名", "学生档案");
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