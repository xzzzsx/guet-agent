package com.atguigu.web.controller.chat;

import java.util.List;

import com.atguigu.system.domain.ChatProject;
import com.atguigu.system.service.IChatProjectService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.atguigu.common.annotation.Log;
import com.atguigu.common.core.controller.BaseController;
import com.atguigu.common.core.domain.AjaxResult;
import com.atguigu.common.enums.BusinessType;
import com.atguigu.common.utils.poi.ExcelUtil;
import com.atguigu.common.core.page.TableDataInfo;

/**
 * 项目配置Controller
 * 
 * @author lixianfeng
 * @date 2025-05-21
 */
@RestController
@RequestMapping("/chat/project")
public class ChatProjectController extends BaseController
{
    @Autowired
    private IChatProjectService chatProjectService;

    /**
     * 查询项目配置列表
     */
    @PreAuthorize("@ss.hasPermi('chat:project:list')")
    @GetMapping("/list")
    public TableDataInfo list(ChatProject chatProject)
    {
        startPage();
        List<ChatProject> list = chatProjectService.selectChatProjectList(chatProject);
        return getDataTable(list);
    }

    /**
     * 导出项目配置列表
     */
    @PreAuthorize("@ss.hasPermi('chat:project:export')")
    @Log(title = "项目配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ChatProject chatProject)
    {
        List<ChatProject> list = chatProjectService.selectChatProjectList(chatProject);
        ExcelUtil<ChatProject> util = new ExcelUtil<ChatProject>(ChatProject.class);
        util.exportExcel(response, list, "项目配置数据");
    }

    /**
     * 获取项目配置详细信息
     */
    @PreAuthorize("@ss.hasPermi('chat:project:queryVo')")
    @GetMapping(value = "/{projectId}")
    public AjaxResult getInfo(@PathVariable("projectId") Long projectId)
    {
        return success(chatProjectService.selectChatProjectByProjectId(projectId));
    }

    /**
     * 新增项目配置
     */
    @PreAuthorize("@ss.hasPermi('chat:project:add')")
    @Log(title = "项目配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ChatProject chatProject)
    {
        return toAjax(chatProjectService.insertChatProject(chatProject));
    }

    /**
     * 修改项目配置
     */
    @PreAuthorize("@ss.hasPermi('chat:project:edit')")
    @Log(title = "项目配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ChatProject chatProject)
    {
        return toAjax(chatProjectService.updateChatProject(chatProject));
    }

    /**
     * 删除项目配置
     */
    @PreAuthorize("@ss.hasPermi('chat:project:remove')")
    @Log(title = "项目配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{projectIds}")
    public AjaxResult remove(@PathVariable Long[] projectIds)
    {
        return toAjax(chatProjectService.deleteChatProjectByProjectIds(projectIds));
    }
}