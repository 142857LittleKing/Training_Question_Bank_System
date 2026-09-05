package com.questionbank.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** ai.* 配置 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(String provider, Qianfan qianfan) {

    public record Qianfan(String baseUrl, String apiKey, String model,
                          Double temperature, Integer maxTokens,
                          Integer timeoutSeconds, Boolean responseFormat) {
    }
}
