package com.atguigu.system.service.impl;

import java.util.List;
import com.atguigu.common.utils.DateUtils;
import com.atguigu.system.domain.ChatProject;
import com.atguigu.system.mapper.ChatProjectMapper;
import com.atguigu.system.service.IChatProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 新增
import com.atguigu.system.mapper.ChatKnowledgeMapper;

/**
 * 项目配置Service业务层处理
 * 
 * @author lixianfeng
 * @date 2025-05-21
 */
@Service
public class ChatProjectServiceImpl implements IChatProjectService
{
    @Autowired
    private ChatProjectMapper chatProjectMapper;

    // 注入知识库Mapper用于级联删除
    @Autowired
    private ChatKnowledgeMapper chatKnowledgeMapper;

    /**
     * 查询项目配置
     * 
     * @param projectId 项目配置主键
     * @return 项目配置
     */
    @Override
    public ChatProject selectChatProjectByProjectId(Long projectId)
    {
        return chatProjectMapper.selectChatProjectByProjectId(projectId);
    }

    /**
     * 查询项目配置列表
     * 
     * @param chatProject 项目配置
     * @return 项目配置
     */
    @Override
    public List<ChatProject> selectChatProjectList(ChatProject chatProject)
    {
        return chatProjectMapper.selectChatProjectList(chatProject);
    }

    /**
     * 新增项目配置
     * 
     * @param chatProject 项目配置
     * @return 结果
     */
    @Override
    public int insertChatProject(ChatProject chatProject)
    {
        chatProject.setCreateTime(DateUtils.getNowDate());
        return chatProjectMapper.insertChatProject(chatProject);
    }

    /**
     * 修改项目配置
     * 
     * @param chatProject 项目配置
     * @return 结果
     */
    @Override
    public int updateChatProject(ChatProject chatProject)
    {
        chatProject.setUpdateTime(DateUtils.getNowDate());
        return chatProjectMapper.updateChatProject(chatProject);
    }

    /**
     * 批量删除项目配置
     * 
     * @param projectIds 需要删除的项目配置主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteChatProjectByProjectIds(Long[] projectIds)
    {
        // 先删除每个项目下的知识库记录
        for (Long pid : projectIds) {
            chatKnowledgeMapper.deleteByProjectId(pid);
        }
        // 再删除项目
        return chatProjectMapper.deleteChatProjectByProjectIds(projectIds);
    }

    /**
     * 删除项目配置信息
     * 
     * @param projectId 项目配置主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteChatProjectByProjectId(Long projectId)
    {
        chatKnowledgeMapper.deleteByProjectId(projectId);
        return chatProjectMapper.deleteChatProjectByProjectId(projectId);
    }
}
