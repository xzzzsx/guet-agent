package com.atguigu.guliai.strategy;

import com.atguigu.guliai.vo.QueryVo;
import com.atguigu.system.domain.ChatKnowledge;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 抽象策略类
 */
public interface AiOperator {

    /**
     * 向向量数据库中添加文档方法
     * @param chatKnowledge
     */
    void addDocs(ChatKnowledge chatKnowledge);

    /**
     * 流式聊天方法
     * @param messages
     * @return
     */
    // 修改方法签名，接受数组而不是List
    Flux<String> chat_stream(org.springframework.ai.chat.messages.Message[] messages);
}
