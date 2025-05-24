package com.atguigu.ai;

import com.atguigu.guliai.pojo.Chat;
import com.atguigu.guliai.service.AiService;
import com.atguigu.guliai.vo.ChatVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("list-chat")
    public List<Chat> listChat(Long projectId, Long userId){
        List<Chat> chats = this.aiService.listChat(projectId, userId);
        return chats;
    }
}
