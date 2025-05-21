package com.atguigu.web.controller.chat;

import java.util.List;

import com.atguigu.system.domain.ChatKnowledge;
import com.atguigu.system.service.IChatKnowledgeService;
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
 * 知识库管理Controller
 * 
 * @author lixianfeng
 * @date 2025-05-21
 */
@RestController
@RequestMapping("/chat/knowledge")
public class ChatKnowledgeController extends BaseController
{
    @Autowired
    private IChatKnowledgeService chatKnowledgeService;

    /**
     * 查询知识库管理列表
     */
    @PreAuthorize("@ss.hasPermi('chat:knowledge:list')")
    @GetMapping("/list")
    public TableDataInfo list(ChatKnowledge chatKnowledge)
    {
        startPage();
        List<ChatKnowledge> list = chatKnowledgeService.selectChatKnowledgeList(chatKnowledge);
        return getDataTable(list);
    }

    /**
     * 导出知识库管理列表
     */
    @PreAuthorize("@ss.hasPermi('chat:knowledge:export')")
    @Log(title = "知识库管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ChatKnowledge chatKnowledge)
    {
        List<ChatKnowledge> list = chatKnowledgeService.selectChatKnowledgeList(chatKnowledge);
        ExcelUtil<ChatKnowledge> util = new ExcelUtil<ChatKnowledge>(ChatKnowledge.class);
        util.exportExcel(response, list, "知识库管理数据");
    }

    /**
     * 获取知识库管理详细信息
     */
    @PreAuthorize("@ss.hasPermi('chat:knowledge:queryVo')")
    @GetMapping(value = "/{knowledgeId}")
    public AjaxResult getInfo(@PathVariable("knowledgeId") Long knowledgeId)
    {
        return success(chatKnowledgeService.selectChatKnowledgeByKnowledgeId(knowledgeId));
    }

    /**
     * 新增知识库管理
     */
    @PreAuthorize("@ss.hasPermi('chat:knowledge:add')")
    @Log(title = "知识库管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ChatKnowledge chatKnowledge)
    {
        return toAjax(chatKnowledgeService.insertChatKnowledge(chatKnowledge));
    }

    /**
     * 修改知识库管理
     */
    @PreAuthorize("@ss.hasPermi('chat:knowledge:edit')")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ChatKnowledge chatKnowledge)
    {
        return toAjax(chatKnowledgeService.updateChatKnowledge(chatKnowledge));
    }

    /**
     * 删除知识库管理
     */
    @PreAuthorize("@ss.hasPermi('chat:knowledge:remove')")
    @Log(title = "知识库管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{knowledgeIds}")
    public AjaxResult remove(@PathVariable Long[] knowledgeIds)
    {
        return toAjax(chatKnowledgeService.deleteChatKnowledgeByKnowledgeIds(knowledgeIds));
    }
}
