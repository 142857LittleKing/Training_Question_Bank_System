package com.questionbank.ai;

import org.springframework.stereotype.Component;

/** 依据配置选择出题源: auto | qianfan | mock */
@Component
public class AiProviderFactory {

    private final AiProperties properties;

    public AiProviderFactory(AiProperties properties) {
        this.properties = properties;
    }

    public AiProvider get() {
        String provider = properties.provider() == null ? "auto" : properties.provider().trim().toLowerCase();
        boolean keyConfigured = hasApiKey();
        if ("mock".equals(provider)) {
            return new MockAiProvider();
        }
        if ("qianfan".equals(provider) && !keyConfigured) {
            // 配置为 qianfan 但缺少 Key: 直接构造, 调用时给出明确错误
        }
        if ("qianfan".equals(provider) || ("auto".equals(provider) && keyConfigured)) {
            return new QianfanAiProvider(properties);
        }
        return new MockAiProvider();
    }

    public boolean hasApiKey() {
        AiProperties.Qianfan q = properties.qianfan();
        return q != null && q.apiKey() != null && !q.apiKey().isBlank();
    }

    public AiProvider.ProviderInfo info() {
        AiProvider p = get();
        String model = properties.qianfan() == null ? null : properties.qianfan().model();
        return new AiProvider.ProviderInfo(p.name(), p.label(), p.requiresApiKey(),
                hasApiKey(), model, modeTip());
    }

    public String modeTip() {
        if (get().name().equals("mock")) {
            return "当前未配置百度千帆 API Key, 使用本地模拟生成(演示模式)。在环境变量中配置 QIANFAN_API_KEY 后重启即可自动切换为千帆大模型出题。";
        }
        return "当前使用百度千帆大模型出题。";
    }
}
