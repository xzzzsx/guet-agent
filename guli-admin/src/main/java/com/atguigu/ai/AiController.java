package com.atguigu.ai;

import com.atguigu.guliai.pojo.Chat;
import com.atguigu.guliai.service.AiService;
import com.atguigu.guliai.vo.ChatVo;
import com.atguigu.guliai.vo.QueryVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("ai")
public class AiController {
    @Autowired
    private AiService aiService;

    /**
     * 创建会话
     * @param chatVo
     * @return
     */
    @PostMapping("create-chat")
    public String createChat(@RequestBody ChatVo chatVo){
        String chatId = this.aiService.createChat(chatVo);
        return chatId;
    }

    /**
     * 更新会话
     * @param chatVo
     * @return
     */
    @PostMapping("update-chat")
    public String updateChat(@RequestBody ChatVo chatVo){
        this.aiService.updateChat(chatVo);
        return "更新成功!";
    }

    /**
     * 获取会话列表
     * @param projectId
     * @param userId
     * @return
     */
    @GetMapping("list-chat")
    public List<Chat> listChat(Long projectId, Long userId){
        List<Chat> chats = this.aiService.listChat(projectId, userId);
        return chats;
    }

    //流式回答,并设置中文编码,防止出现编码问题
    @PostMapping(value = "chat-stream", produces = "text/plain;charset=utf-8")
    public Flux<String> chatStream(@RequestBody QueryVo queryVo){
        Flux<String> result = this.aiService.chatStream(queryVo);
        return result;
    }
}
