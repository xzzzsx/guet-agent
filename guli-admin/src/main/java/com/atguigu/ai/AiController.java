package com.atguigu.ai;

import com.atguigu.common.core.domain.AjaxResult;
import com.atguigu.guliai.pojo.Chat;
import com.atguigu.guliai.pojo.Message;
import com.atguigu.guliai.service.AiService;
import com.atguigu.guliai.vo.ChatVo;
import com.atguigu.guliai.vo.MessageVo;
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

    /**
     * 聊天
     * @param queryVo
     * @return
     */
    //流式回答,并设置中文编码,防止出现编码问题
    @PostMapping(value = "chat-stream", produces = "text/plain;charset=utf-8")
    public Flux<String> chatStream(@RequestBody QueryVo queryVo){
        Flux<String> result = this.aiService.chatStream(queryVo);
        return result;
    }

    /**
     * 保存消息
     * @param messageVo
     * @return
     */
    @PostMapping("save-msg")
    public String saveMsg(@RequestBody MessageVo messageVo){
        this.aiService.saveMsg(messageVo);
        return "保存回答成功!";
    }

    /**
     * 查询消息列表
     * @param chatId
     * @return
     */
    @GetMapping("list-msg")
    public List<Message> listMsg(Long chatId){
        List<Message> msgs = this.aiService.listMsg(chatId);
        return msgs;
    }

    /**
     * 删除会话
     * @param chatId
     * @param projectId
     * @return
     */
    @DeleteMapping("delete-chat")
    public AjaxResult deleteChat(Long chatId, Long projectId){
        this.aiService.deleteChat(chatId, projectId);
        return AjaxResult.success();
    }
}
