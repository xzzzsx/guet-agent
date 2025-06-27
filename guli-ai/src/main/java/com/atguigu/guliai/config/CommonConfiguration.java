// package com.atguigu.guliai.config;
// // ... 略
// import com.atguigu.guliai.tools.CourseTools;
// import org.springframework.ai.chat.client.ChatClient;
// import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
// import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
// import org.springframework.ai.chat.memory.ChatMemory;
// import org.springframework.ai.openai.OpenAiChatModel;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
//
//
// @Configuration
// public class CommonConfiguration {
//
//
//     @Bean
//     public ChatClient serviceChatClient(
//             OpenAiChatModel model,
//             ChatMemory chatMemory,
//             CourseTools courseTools) {
//         return ChatClient.builder(model)
//                 .defaultSystem(CUSTOMER_SERVICE_SYSTEM)
//                 .defaultAdvisors(
//                         new MessageChatMemoryAdvisor(chatMemory), // CHAT MEMORY
//                         new SimpleLoggerAdvisor())
//                 .defaultTools(courseTools)
//                 .build();
//     }
// }