package com.atguigu.guliai.stategy;

import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.pojo.Message;
import com.atguigu.guliai.vo.QueryVo;
import com.atguigu.system.domain.ChatKnowledge;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * ollama的具体策略类
 */
@AiBean(SystemConstant.MODEL_TYPE_OLLAMA)
public class OllamaAiOperator implements AiOperator {

    @Autowired
    private QdrantVectorStore ollamaVectorStore;

    @Autowired
    private OllamaChatModel ollamaChatModel;

    @Override
    public void addDocs(ChatKnowledge chatKnowledge) {
        this.ollamaVectorStore.add(List.of(new org.springframework.ai.document.Document(chatKnowledge.getContent(),
                Map.of("projectId", chatKnowledge.getProjectId().toString(), "knowledgeId", chatKnowledge.getKnowledgeId().toString()))));
    }

    private static final Logger log = LoggerFactory.getLogger(OllamaAiOperator.class);

    @Override
    public List<Document> similaritySearch(QueryVo queryVo) {
        SearchRequest request = SearchRequest.builder()
                .query(queryVo.getMsg())  //相似度的查询条件
                .filterExpression(new FilterExpressionBuilder()
                        .eq("projectId", queryVo.getProjectId().toString()).build())  //只查询当前项目的知识库
                .topK(5)  //增加返回文档数量以提高召回率
                .similarityThreshold(0.4f)  //降低阈值以捕获更多潜在相关文档
                .build();
        List<Document> documents = this.ollamaVectorStore.similaritySearch(request);
        // 记录检索结果日志
        log.info("Ollama向量检索: 查询词={}, 项目ID={}, 检索到{}条文档", 
                queryVo.getMsg(), queryVo.getProjectId(), documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            double score = doc.getScore() != null ? doc.getScore() : 0.0d;
            log.info("文档{}: 相似度={}, 内容={}", i+1, score, doc.getText().substring(0, Math.min(100, doc.getText().length())));
        }
        return documents;
    }

    @Override
    public Flux<String> chat_stream(org.springframework.ai.chat.messages.Message[] messages) {
        // 添加系统提示：基于提供的知识库内容回答问题
        org.springframework.ai.chat.messages.Message[] newMessages = new org.springframework.ai.chat.messages.Message[messages.length + 1];
        newMessages[0] = new SystemMessage("请严格基于提供的知识库内容回答问题，不要编造信息。");
        System.arraycopy(messages, 0, newMessages, 1, messages.length);
        return ollamaChatModel.stream(newMessages);
    }
}
