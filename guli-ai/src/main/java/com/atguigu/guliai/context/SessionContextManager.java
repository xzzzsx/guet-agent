// SessionContextManager.java
package com.atguigu.guliai.context;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话上下文管理器
 * 为每个会话ID维护独立的对话上下文
 */
@Component
public class SessionContextManager {
    // 会话上下文存储: <sessionId, 消息列表>
    private final Map<String, List<Message>> sessionContexts = new ConcurrentHashMap<>();
    
    // 最大上下文消息数
    private static final int MAX_CONTEXT_MESSAGES = 10;

    /**
     * 添加消息到会话上下文
     * @param sessionId 会话ID
     * @param message 消息对象
     */
    public void addMessage(String sessionId, Message message) {
        List<Message> messages = sessionContexts.computeIfAbsent(sessionId, k -> new ArrayList<>());
        
        // 维护最大消息数
        if (messages.size() >= MAX_CONTEXT_MESSAGES) {
            messages.remove(0); // 移除最旧的消息
        }
        messages.add(message);
    }

    /**
     * 获取会话上下文
     * @param sessionId 会话ID
     * @return 当前会话的消息列表
     */
    public List<Message> getContext(String sessionId) {
        return sessionContexts.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * 清除会话上下文
     * @param sessionId 会话ID
     */
    public void clearContext(String sessionId) {
        sessionContexts.remove(sessionId);
    }
}