package com.atguigu.guliai.config;

import com.atguigu.guliai.tools.CourseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel; // 新增导入
import com.atguigu.guliai.constant.SystemConstant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class AiAdvisorConfig {

    /**
     * 将ollamaChatModel标记为主要的ChatModel bean
     */
    @Primary
    @Bean
    public ChatModel primaryChatModel(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        return chatModel;
    }
    // /**
    /**
     * 创建并返回一个ChatClient的Spring Bean实例。
     *
     * @param builder 用于构建ChatClient实例的构建者对象
     * @param courseTools 课程工具类实例
     * @return 构建好的ChatClient实例
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                               Advisor simpleLoggerAdvisor,
                               CourseTools courseTools) {
        return builder
                .defaultSystem(SystemConstant.CUSTOMER_SERVICE_SYSTEM)
                .defaultAdvisors(simpleLoggerAdvisor)
                .defaultTools(courseTools)  // 确保课程工具已注册
                .build();
    }
    //
    /**
     * 创建并返回一个SimpleLoggerAdvisor的Spring Bean实例。
     */
    @Bean
    public Advisor simpleLoggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    /**
     * 创建并返回一个SafeGuardAdvisor的Spring Bean实例。
     */
    @Bean
    public List<String> sensitiveWords() {
        // 敏感词列表（示例数据，建议实际使用时从配置文件或数据库读取）
        return List.of("敏感词1", "敏感词2");
    }

    @Bean
    public Advisor safeGuardAdvisor(List<String> sensitiveWords) {
        // 创建安全防护Advisor，参数依次为：敏感词库、违规提示语、advisor处理优先级，数字越小越优先
        return new SafeGuardAdvisor(
                sensitiveWords,
                "敏感词提示：请勿输入敏感词！",
                Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER
        );
    }
}
