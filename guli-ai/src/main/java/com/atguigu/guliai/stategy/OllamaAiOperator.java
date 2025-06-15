package com.atguigu.guliai.stategy;

import com.atguigu.common.utils.StringUtils;
import com.atguigu.guliai.constant.SystemConstant;
import com.atguigu.guliai.pojo.Message;
import com.atguigu.guliai.vo.QueryVo;
import com.atguigu.system.domain.ChatKnowledge;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
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

import java.util.*;
import java.util.stream.Collectors;

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
                .topK(10)  //增加返回文档数量确保相关内容被包含
                .similarityThreshold(0.2f)  //降低阈值以提高召回率
                .build();
        List<Document> documents = this.ollamaVectorStore.similaritySearch(request);
        // 记录检索结果日志
        log.info("Ollama向量检索: 查询词={}, 项目ID={}, 检索到{}条文档", queryVo.getMsg(), queryVo.getProjectId(), documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            double score = doc.getScore() != null ? doc.getScore() : 0.0d;
            String content = doc.getText() != null ? doc.getText().substring(0, Math.min(200, doc.getText().length())) : "无内容";
            log.info("文档{}: 相似度={}, 内容={}", i+1, score, content);
        }
        String queryText = queryVo.getMsg().toLowerCase();
        // 使用Jieba分词提取中文关键词
        JiebaSegmenter segmenter = new JiebaSegmenter();
        List<SegToken> segTokens = segmenter.process(queryText, JiebaSegmenter.SegMode.SEARCH);
        List<String> keywords = segTokens.stream()
                .map(token -> token.word)  // 新版属性访问方式
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toList());
        // 添加完整查询词作为关键词
        keywords.add(queryText);
        
        // 输出关键词拆分日志
        log.info("查询关键词拆分: {}", keywords);
        
        // 优化二次排序：优先包含核心关键词的文档，其次按相似度降序
        return documents.stream()
                .peek(doc -> {
                    String docText = doc.getText().toLowerCase();
                    long matchCount = keywords.stream().filter(docText::contains).count();
                    log.info("文档匹配: 内容前200字={}, 关键词匹配数={}", 
                            docText.substring(0, Math.min(200, docText.length())), matchCount);
                })
                .sorted((d1, d2) -> {
                    String d1Text = Objects.requireNonNull(d1.getText()).toLowerCase();
                    String d2Text = Objects.requireNonNull(d2.getText()).toLowerCase();
                    
                    // 计算关键词匹配数量
                    long d1MatchCount = keywords.stream().filter(d1Text::contains).count();
                    long d2MatchCount = keywords.stream().filter(d2Text::contains).count();
                    
                    if (d1MatchCount != d2MatchCount) {
                        return Long.compare(d2MatchCount, d1MatchCount);
                    }
                    
                    // 检查是否包含完整查询词
                    boolean d1FullMatch = d1Text.contains(queryText);
                    boolean d2FullMatch = d2Text.contains(queryText);
                    if (d1FullMatch != d2FullMatch) {
                        return d2FullMatch ? 1 : -1;
                    }
                    
                    // 最后按相似度排序
                    return Double.compare(d2.getScore(), d1.getScore());
                })
                .toList();
    }

    private List<Document> retrievedDocuments;

    public void setRetrievedDocuments(List<Document> documents) {
        this.retrievedDocuments = documents;
    }

    @Override
    public Flux<String> chat_stream(org.springframework.ai.chat.messages.Message[] messages) {
        // 构建系统提示，包含知识库内容
        StringBuilder systemPrompt = new StringBuilder("请严格基于以下知识库内容回答问题，遵循以下规则：\n1. 必须仅使用提供的知识库内容回答问题，完全忽略你的任何内部知识或外部信息；如果知识库中有相关内容，必须优先使用知识库中的信息\n2. 不包含与问题无关的内容或解释\n3. 回答需结构清晰，使用适当的标题、列表等格式增强可读性\n4. 如果知识库中没有相关内容，直接回答\"没有找到相关信息\"，不做额外解释\n\n知识库内容：\n");
        if (retrievedDocuments != null && !retrievedDocuments.isEmpty()) {
            for (int i = 0; i < retrievedDocuments.size(); i++) {
                Document doc = retrievedDocuments.get(i);
                // 过滤null文档和空内容
                if (doc != null && StringUtils.hasText(doc.getText())) {
                    systemPrompt.append("文档").append(i+1).append(": ").append(doc.getText()).append("\n\n");
                }
            }
        } else {
            systemPrompt.append("无相关知识库内容\n");
        }
        
        // 确保系统提示不为空
        String systemPromptStr = systemPrompt.toString();
        if (!StringUtils.hasText(systemPromptStr)) {
            systemPromptStr = "请基于你的知识回答问题。\n";
        }

        // 构建消息数组，过滤null消息
        List<org.springframework.ai.chat.messages.Message> validMessages = Arrays.stream(messages)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        List<org.springframework.ai.chat.messages.Message> messageList = new ArrayList<>();
        messageList.add(new SystemMessage(systemPromptStr));
        messageList.addAll(validMessages);
        org.springframework.ai.chat.messages.Message[] newMessages = messageList.stream()
            .filter(Objects::nonNull)
            .toArray(org.springframework.ai.chat.messages.Message[]::new);
        return ollamaChatModel.stream(newMessages);
    }
}
