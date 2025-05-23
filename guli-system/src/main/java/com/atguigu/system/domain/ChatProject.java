package com.atguigu.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.atguigu.common.annotation.Excel;
import com.atguigu.common.core.domain.BaseEntity;

/**
 * 项目配置对象 chat_project
 * 
 * @author lixianfeng
 * @date 2025-05-21
 */
public class ChatProject extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 项目主键 */
    private Long projectId;

    /** 项目名称 */
    @Excel(name = "项目名称")
    private String projectName;

    /** 模型类型：ollama、openai */
    @Excel(name = "模型类型：ollama、openai")
    private String type;

    /** 具体模型：deepseek-r1:7B、gpt-3.5-turbo */
    @Excel(name = "具体模型：deepseek-r1:7B、gpt-3.5-turbo")
    private String model;

    public void setProjectId(Long projectId) 
    {
        this.projectId = projectId;
    }

    public Long getProjectId() 
    {
        return projectId;
    }
    public void setProjectName(String projectName) 
    {
        this.projectName = projectName;
    }

    public String getProjectName() 
    {
        return projectName;
    }
    public void setType(String type) 
    {
        this.type = type;
    }

    public String getType() 
    {
        return type;
    }
    public void setModel(String model) 
    {
        this.model = model;
    }

    public String getModel() 
    {
        return model;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("projectId", getProjectId())
            .append("projectName", getProjectName())
            .append("type", getType())
            .append("model", getModel())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
