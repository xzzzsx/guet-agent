package com.atguigu.guliai.config;

import com.atguigu.guliai.constant.SystemConstant;
import io.qdrant.client.QdrantClient;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为了解决
 * Parameter 0 of method vectorStore in org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration required a single bean, but 2 were found:
 * 	- ollamaEmbeddingModel: defined by method 'ollamaEmbeddingModel' in class path resource [org/springframework/ai/model/ollama/autoconfigure/OllamaEmbeddingAutoConfiguration.class]
 * 	- openAiEmbeddingModel: defined by method 'openAiEmbeddingModel' in class path resource [org/springframework/ai/model/openai/autoconfigure/OpenAiEmbeddingAutoConfiguration.class]
 * 	手动配置向量数据库的初始化
 */
@Configuration
public class QdrantVectorStoreConfig {

    @Autowired
    QdrantClient qdrantClient;

    @Autowired
    QdrantVectorStoreProperties properties;

    /**
     * OpenAi向量数据库
     * @param openAiEmbeddingModel
     * @return
     */
    @Bean
    public QdrantVectorStore openAiVectorStore(OpenAiEmbeddingModel openAiEmbeddingModel) {
        //不同的大模型使用的维度是不同的
        return QdrantVectorStore.builder(qdrantClient, openAiEmbeddingModel) // 传入必需参数
                .collectionName(SystemConstant.VECTOR_STORE_OPENAI)
                .initializeSchema(true)  // 强制初始化schema确保维度配置生效
                .build();
    }

    /**
     * Ollama向量数据库
     * @param ollamaEmbeddingModel
     * @return
     */
    @Bean
    public QdrantVectorStore ollamaVectorStore(OllamaEmbeddingModel ollamaEmbeddingModel) {
        return QdrantVectorStore.builder(qdrantClient, ollamaEmbeddingModel) // 传入必需参数
                // .collectionName(SystemConstant.VECTOR_STORE_OLLAMA)
                .initializeSchema(true)  // 强制初始化schema确保维度配置生效
                .build();
    }
}