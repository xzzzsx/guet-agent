package com.atguigu.guliai.agent;

import com.atguigu.guliai.pojo.Message;
import com.atguigu.guliai.service.AiService;
import com.atguigu.guliai.vo.QueryVo;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAgent implements Agent {
    @Autowired
    protected AiService aiService;

    // 方法保持不变，使用自定义Message类型
    protected Flux<String> baseChatStream(List<Message> historyMessages, Long projectId, String sessionId) {
        // 创建QueryVo对象（包含历史消息）
        QueryVo queryVo = new QueryVo();
        queryVo.setProjectId(projectId);
        queryVo.setChatId(sessionId != null ? Long.parseLong(sessionId) : null);

        // 设置历史消息到QueryVo
        if (!historyMessages.isEmpty()) {
            // 获取最后一条用户消息作为当前问题
            Message lastUserMessage = historyMessages.stream()
                    .filter(msg -> msg.getType() == 0)
                    .reduce((first, second) -> second)
                    .orElse(null);

            if (lastUserMessage != null) {
                queryVo.setMsg(lastUserMessage.getContent());
            }
        }

        // 设置完整历史消息
        queryVo.setHistoryMessages(historyMessages); // 使用新增的字段

        // 调用AiService的直接处理方法
        return aiService.directModelProcessing(queryVo);
    }

    /**
     * 获取最近5条用户消息（包含当前消息）
     *
     * @param historyMessages 完整历史消息列表
     * @return 最近5条用户消息（时间倒序：index0=最新消息）
     */
    protected List<String> getRecentUserMessages(List<Message> historyMessages) {
        List<String> recentMessages = new ArrayList<>();
        int count = 0;

        // 逆序遍历消息列表，只收集用户消息
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            Message msg = historyMessages.get(i);
            if (msg.getType() == 0) { // 0 表示用户消息
                recentMessages.add(msg.getContent());
                count++;
                if (count >= 5) break; // 只取最近5条
            }
        }
        return recentMessages;
    }

    public abstract Object[] tools();
}