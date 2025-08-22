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
}