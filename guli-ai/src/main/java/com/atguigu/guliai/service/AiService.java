package com.atguigu.guliai.service;

import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.utils.FileUtil;
import com.atguigu.system.domain.ChatKnowledge;
import com.atguigu.system.domain.ChatProject;
import com.atguigu.system.mapper.ChatKnowledgeMapper;
import com.atguigu.system.mapper.ChatProjectMapper;
import com.atguigu.system.service.IChatKnowledgeService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Autowired
    private QdrantVectorStore openAiVectorStore;

    @Autowired
    private QdrantVectorStore ollamaVectorStore;

    @Autowired
    private ChatKnowledgeMapper chatKnowledgeMapper;

    @Autowired
    private ChatProjectMapper chatProjectMapper;
    @Transactional
    public void upload(ChatKnowledge chatKnowledge, MultipartFile file) {
        //从文件中把内容取出来,通过工具类
        String content = FileUtil.getContentFromFile(file);

        //保存知识库到本地数据库(mysql)
        chatKnowledge.setFileName(file.getOriginalFilename());
        chatKnowledge.setContent(content);
        this.chatKnowledgeMapper.insertChatKnowledge(chatKnowledge);

        //保存知识库到向量数据库:projectId knowledgeId content
        //根据projectId查询项目(模型的类型)
        ChatProject chatProject = this.chatProjectMapper.selectChatProjectByProjectId(chatKnowledge.getProjectId());

        if (chatProject.getType().equals(SystemConstant.MODEL_TYPE_OPENAI)) {
            //保存到向量数据库
            this.openAiVectorStore.add(List.of(new Document(chatKnowledge.getContent(),
                    Map.of("projectId", chatKnowledge.getProjectId().toString(), "knowledgeId", chatKnowledge.getKnowledgeId().toString()))));
        }else if (chatProject.getType().equals(SystemConstant.MODEL_TYPE_OLLAMA)) {
            //保存到向量数据库
            this.ollamaVectorStore.add(List.of(new Document(chatKnowledge.getContent(),
                    Map.of("projectId", chatKnowledge.getProjectId().toString(), "knowledgeId", chatKnowledge.getKnowledgeId().toString()))));
        }

    }
}
