package com.atguigu.guliai.stategy;

import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.pojo.Message;
import com.atguigu.guliai.vo.QueryVo;
import com.atguigu.system.domain.ChatKnowledge;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * openai的具体策略类
 */
@AiBean(SystemConstant.MODEL_TYPE_OPENAI)
public class OpenAiOperator implements AiOperator {

    @Autowired
    private QdrantVectorStore openAiVectorStore;

    @Autowired
    private OpenAiChatModel openAiChatModel;

    @Override
    public void addDocs(ChatKnowledge chatKnowledge) {
        this.openAiVectorStore.add(List.of(new org.springframework.ai.document.Document(chatKnowledge.getContent(),
                Map.of("projectId", chatKnowledge.getProjectId().toString(), "knowledgeId", chatKnowledge.getKnowledgeId().toString()))));
    }

    @Override
    public List<Document> similaritySearch(QueryVo queryVo) {
        SearchRequest request = SearchRequest.builder()
                .query(queryVo.getMsg())  //相似度的查询条件
                .filterExpression(new FilterExpressionBuilder()
                        .eq("projectId", queryVo.getProjectId().toString()).build())  //只查询当前项目的知识库
                .topK(SystemConstant.TOP_K)  //相似度排名前3
                .build();

        return this.openAiVectorStore.similaritySearch(request);
    }

    @Override
    public Flux<String> chat_stream(org.springframework.ai.chat.messages.Message[] messages) {
        // 确保传入的数组类型正确
        return openAiChatModel.stream(messages);
    }
}
