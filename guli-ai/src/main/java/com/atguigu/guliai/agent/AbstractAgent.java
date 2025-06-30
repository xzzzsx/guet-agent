package com.atguigu.guliai.agent;

import cn.hutool.core.util.IdUtil;
import com.atguigu.guliai.config.AiAdvisorConfig;
import com.atguigu.guliai.service.AiService;
import com.atguigu.guliai.vo.QueryVo;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

public abstract class AbstractAgent implements Agent {
    @Autowired
    protected AiService aiService;

    // 增加projectId参数传递
    protected Flux<String> baseChatStream(String question, Long projectId, String sessionId) {
        // 创建基本的QueryVo对象（修复：正确设置项目ID和会话ID）
        QueryVo queryVo = new QueryVo();
        queryVo.setMsg(question);
        queryVo.setProjectId(projectId); // 关键修复：设置项目ID
        queryVo.setChatId(sessionId != null ? Long.parseLong(sessionId) : null);

        // 重要修改：调用AiService的直接处理方法，绕过智能体路由系统
        return aiService.directModelProcessing(queryVo);
    }
}