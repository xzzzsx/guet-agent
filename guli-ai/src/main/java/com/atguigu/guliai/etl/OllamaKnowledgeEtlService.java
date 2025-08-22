package com.atguigu.guliai.etl;

import com.atguigu.common.utils.StringUtils;
import com.atguigu.system.domain.ChatKnowledge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用 Spring AI 官方 ETL 规范实现：
 * Extract（DocumentReader） -> Transform（DocumentTransformer） -> Load（DocumentWriter）
 * 仅用于 Ollama RAG 的数据注入流程。
 */
@Slf4j
@Component
public class OllamaKnowledgeEtlService {

    private static final int DEFAULT_CHUNK_SIZE = 500;

    private final QdrantVectorStore ollamaVectorStore;
    private final OllamaChatModel ollamaChatModel;

    public OllamaKnowledgeEtlService(QdrantVectorStore ollamaVectorStore,
                                     OllamaChatModel ollamaChatModel) {
        this.ollamaVectorStore = ollamaVectorStore;
        this.ollamaChatModel = ollamaChatModel;
    }

    /**
     * 入口：对单条知识库进行完整 ETL 并写入向量库。
     */
    public void etlIngest(ChatKnowledge chatKnowledge) {
        if (chatKnowledge == null || StringUtils.isEmpty(chatKnowledge.getContent())) {
            log.warn("知识库内容为空，跳过ETL");
            return;
        }
        List<Document> extracted = extract(chatKnowledge);
        List<Document> transformed = transform(extracted, chatKnowledge);
        load(transformed);
    }

    /**
     * Extract：从 DB 中的文本内容构造 Resource，并根据后缀选择 Text/Markdown Reader。
     */
    public List<Document> extract(ChatKnowledge chatKnowledge) {
        String fileName = chatKnowledge.getFileName();
        byte[] bytes = chatKnowledge.getContent().getBytes(StandardCharsets.UTF_8);
        Resource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName != null ? fileName : "unknown.txt";
            }
        };

        Map<String, Object> commonMetadata = new HashMap<>();
        commonMetadata.put("projectId", String.valueOf(chatKnowledge.getProjectId()));
        commonMetadata.put("knowledgeId", String.valueOf(chatKnowledge.getKnowledgeId()));
        commonMetadata.put("fileName", fileName);

        List<Document> documents = new ArrayList<>();

        if (fileName != null && fileName.toLowerCase().endsWith(".md")) {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .withAdditionalMetadata("projectId", String.valueOf(chatKnowledge.getProjectId()))
                    .withAdditionalMetadata("knowledgeId", String.valueOf(chatKnowledge.getKnowledgeId()))
                    .withAdditionalMetadata("fileName", fileName)
                    .build();
            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
            documents.addAll(reader.read());
        } else {
            TextReader textReader = new TextReader(resource);
            textReader.getCustomMetadata().putAll(commonMetadata);
            documents.addAll(textReader.read());
        }

        if (documents.isEmpty()) {
            // 兜底：如果 Reader 未能产出文档，则手动构造一条 Document
            documents.add(new Document(chatKnowledge.getContent(), commonMetadata));
        }

        return documents;
    }

    /**
     * Transform：分块 + 关键词增强（基于 OllamaChatModel）。
     */
    public List<Document> transform(List<Document> documents, ChatKnowledge chatKnowledge) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        TokenTextSplitter splitter = new TokenTextSplitter(DEFAULT_CHUNK_SIZE, 100, 10, 5000, true);
        List<Document> chunks = splitter.apply(documents);

        for (Document chunk : chunks) {
            chunk.getMetadata().put("chunkSize", String.valueOf(DEFAULT_CHUNK_SIZE));
            chunk.getMetadata().put("knowledgeId", String.valueOf(chatKnowledge.getKnowledgeId()));
        }

        KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(this.ollamaChatModel, 8);
        return enricher.apply(chunks);
    }

    /**
     * Load：写入向量数据库。
     */
    public void load(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("无文档可写入向量库，跳过Load");
            return;
        }
        new VectorStoreWriter(this.ollamaVectorStore).write(documents);
        log.info("写入向量库完成，文档分块数：{}", documents.size());
    }

    /**
     * 基于 VectorStore 的 DocumentWriter 实现。
     */
    static class VectorStoreWriter implements DocumentWriter {
        private final VectorStore vectorStore;

        VectorStoreWriter(VectorStore vectorStore) {
            this.vectorStore = vectorStore;
        }

        @Override
        public void accept(List<Document> documents) {
            this.vectorStore.add(documents);
        }

        public void write(List<Document> documents) {
            accept(documents);
        }
    }
} 