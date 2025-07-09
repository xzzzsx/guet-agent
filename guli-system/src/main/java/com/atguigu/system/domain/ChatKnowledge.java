package com.atguigu.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.atguigu.common.annotation.Excel;
import com.atguigu.common.core.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;

/**
 * 知识库管理对象 chat_knowledge
 * 
 * @author lixianfeng
 * @date 2025-05-21
 */
public class ChatKnowledge extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** $column.columnComment */
    private Long knowledgeId;

    /** 用户id */
    @Excel(name = "用户id")
    private Long userId;

    /** 项目id */
    @Excel(name = "项目id")
    private Long projectId;

    /** 文件名 */
    @Excel(name = "文件名")
    private String fileName;

    /** 文件内容 */
    @Excel(name = "文件内容")
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String content;

    public void setKnowledgeId(Long knowledgeId) 
    {
        this.knowledgeId = knowledgeId;
    }

    public Long getKnowledgeId() 
    {
        return knowledgeId;
    }
    public void setUserId(Long userId) 
    {
        this.userId = userId;
    }

    public Long getUserId() 
    {
        return userId;
    }
    public void setProjectId(Long projectId) 
    {
        this.projectId = projectId;
    }

    public Long getProjectId() 
    {
        return projectId;
    }
    public void setFileName(String fileName) 
    {
        this.fileName = fileName;
    }

    public String getFileName() 
    {
        return fileName;
    }
    public void setContent(String content) 
    {
        this.content = content;
    }

    public String getContent() 
    {
        return content;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("knowledgeId", getKnowledgeId())
            .append("userId", getUserId())
            .append("projectId", getProjectId())
            .append("fileName", getFileName())
            .append("content", getContent())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
