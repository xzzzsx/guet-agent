package com.atguigu.guliai.service;

import com.atguigu.guliai.domain.ChatKnowledge;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Autowired
    private QdrantVectorStore openAiVectorStore;

    private QdrantVectorStore ollamaVectorStore;

    public void saveKnowledge(ChatKnowledge chatKnowledge) {
        //保存到向量数据库
        this.openAiVectorStore.add(List.of(new Document(chatKnowledge.getContent(),
                Map.of("projectId", chatKnowledge.getProjectId(), "knowledgeId", chatKnowledge.getKnowledgeId()))));
    }
}
