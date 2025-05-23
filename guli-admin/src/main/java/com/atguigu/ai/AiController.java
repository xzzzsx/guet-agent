package com.atguigu.ai;

import com.atguigu.guliai.pojo.Chat;
import com.atguigu.guliai.service.AiService;
import com.atguigu.guliai.vo.ChatVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
