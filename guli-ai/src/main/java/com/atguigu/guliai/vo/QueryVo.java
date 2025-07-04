package com.atguigu.guliai.vo;

import com.atguigu.guliai.pojo.Message; // 新增导入
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List; // 新增导入

@Data
public class QueryVo {

    @Schema(name = "projectId", description = "项目id，不同项目不同本地知识库")
    @NotNull
    private Long projectId;

    @Schema(name = "chatId", description = "聊天记录id，如果该id为null则不会保存会话的历史记录")
    private Long chatId;

    @Schema(name = "language", description = "语言：英文(1)、中文(0)，文件上传删除时不需要该参数")
    @NotNull
    private Integer language = 0;

    @Schema(name = "msg", description = "用户的提问内容，文件上传删除时不需要该参数")
    @NotNull
    private String msg;

    // 新增历史消息字段
    @Schema(name = "historyMessages", description = "历史消息列表")
    private List<Message> historyMessages;
}