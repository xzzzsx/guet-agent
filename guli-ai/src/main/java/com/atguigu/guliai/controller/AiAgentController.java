package com.atguigu.guliai.controller;

import com.atguigu.guliai.service.AiService;
import com.atguigu.guliai.vo.QueryVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

// com.atguigu.guliai.controller.AiController (新增)
@RestController
@RequestMapping("/ai/agent")
public class AiAgentController {
    @Autowired
    private AiService aiService;
    
    @PostMapping("/chat")
    public Flux<String> agentChat(@RequestBody QueryVo queryVo) {
        return aiService.chatStream(queryVo);
    }
}