package com.atguigu.guliai.strategy;

import com.atguigu.guliai.constant.SystemConstant;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 AI 的文档元信息增强器（为文档补充关键词元信息）
 */
@Component
public class MyKeywordEnricher {

    @Autowired
    private ChatModel ollamaChatModel;
    /**
     * 为文档列表添加关键词元信息，提升可搜索性
     *
     * @param documents 待增强的文档列表
     * @return 增强后的文档列表
     */
    public List<Document> enrichDocuments(List<Document> documents) {
        return documents.stream().map(this::extractKeywords).collect(Collectors.toList());
    }

    private Document extractKeywords(Document document) {
        try {
            String prompt = """
                    请从文本中提取最具检索价值的关键词：
                    1. 提取3-5个最能代表文本内容的关键词
                    2. 可以是名词、动词或形容词
                    3. 关键词长度不限但需精炼
                    4. 必须用单个空格分隔
                    5. 示例：
                       输入：学校东区晚上有很多小吃摊
                       输出：东区 夜间餐饮 小吃摊
                       
                    文本内容：{text}
                    
                    关键词：""";

            PromptTemplate template = new PromptTemplate(prompt);
            String keywords = ChatClient.create(ollamaChatModel)
                    .prompt()
                    .user(template.render(Map.of("text", document.getText())))
                    .call()
                    .content();

            // 格式化处理
            String cleanKeywords = keywords.replace("输出", "")
                    .replaceAll("[^\\w\u4e00-\u9fa5]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            // 将关键词添加到文档元数据
            document.getMetadata().put("keywords", cleanKeywords);
            return document;
        } catch (Exception e) {
            // 出错时返回原始文档
            return document;
        }
    }
}