package com.atguigu.guliai.tools;

import com.atguigu.guliai.pojo.Chat;
import com.atguigu.guliai.utils.MongoUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseQueryTools {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Tool(name = "queryChatList", description = "根据项目ID和用户ID查询聊天会话列表")
    public List<Chat> queryChatList(
            @ToolParam(description = "项目ID") Long projectId,
            @ToolParam(description = "用户ID") Long userId) {
        Query query = Query.query(
                Criteria.where("projectId").is(projectId)
                        .and("userId").is(userId)
        ).with(Sort.by(Sort.Order.desc("createTime")));

        return mongoTemplate.find(
                query,
                Chat.class,
                MongoUtil.getChatCollectionName(projectId)
        );
    }
}