package com.atguigu.guliai.config;

import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class AiConfig {

    private final ResourceLoader resourceLoader;

    public AiConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    // 移除错误的TikaDocumentReader配置，因为当前场景不需要预定义该Bean
    // @Bean
    // public TikaDocumentReader tikaDocumentReader() {
    //     return new TikaDocumentReader((Resource) resourceLoader); // 这行存在类型转换错误
    // }
}