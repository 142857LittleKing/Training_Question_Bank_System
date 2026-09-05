package com.questionbank.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.questionbank.ai.AiProvider.GenRequest;
import com.questionbank.common.BizException;
import com.questionbank.common.JsonUtils;
import com.questionbank.common.enums.QuestionType;
import com.questionbank.dto.QuestionDtos.QuestionPayload;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 百度智能云千帆 ModelBuilder 出题实现(OpenAI 兼容 v2 接口)。
 * 文档: https://cloud.baidu.com/doc/WENXINWORKSHOP/index.html
 * 需要环境变量: QIANFAN_API_KEY(千帆控制台 -> 安全认证/API Key), QIANFAN_MODEL
 */
public class QianfanAiProvider implements AiProvider {

    private final AiProperties properties;
    private final RestClient restClient;

    public QianfanAiProvider(AiProperties properties) {
        this.properties = properties;
        AiProperties.Qianfan q = properties.qianfan();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutSec = q.timeoutSeconds() == null ? 180 : q.timeoutSeconds();
        factory.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSec).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(q.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public String name() {
        return "qianfan";
    }

    @Override
    public String label() {
        return "百度千帆大模型";
    }

    @Override
    public boolean requiresApiKey() {
        return true;
    }

    @Override
    public List<QuestionPayload> generate(GenRequest req) {
        AiProperties.Qianfan q = properties.qianfan();
        if (q.apiKey() == null || q.apiKey().isBlank()) {
            throw BizException.bad("未配置千帆 API Key, 请在环境变量 QIANFAN_API_KEY 中配置(或使用 provider=mock 演示)");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", q.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", buildUserPrompt(req))));
        body.put("temperature", q.temperature() == null ? 0.6 : q.temperature());
        if (q.maxTokens() != null) {
            body.put("max_tokens", q.maxTokens());
        }
        body.put("stream", false);
        if (Boolean.TRUE.equals(q.responseFormat())) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        String content;
        try {
            String resp = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + q.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = resp == null ? null : JsonUtils.readTree(resp);
            if (root == null) {
                throw new IllegalStateException("模型返回内容为空或非 JSON");
            }
            JsonNode error = root.get("error");
            if (error != null) {
                throw new IllegalStateException("千帆接口错误: " + error);
            }
            JsonNode choice = root.path("choices").path(0);
            content = choice.path("message").path("content").asText(null);
            if (content == null) {
                throw new IllegalStateException("响应中没有 choices[0].message.content, 原始响应: "
                        + (resp == null ? "" : resp.substring(0, Math.min(resp.length(), 400))));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // RestClient 4xx/5xx 会抛 RestClientResponseException, 尽量透出千帆错误正文
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            throw BizException.bad("千帆大模型调用失败: " + (msg.length() > 500 ? msg.substring(0, 500) : msg));
        }

        try {
            List<QuestionPayload> parsed = AiResponseParser.parse(content);
            if (parsed.isEmpty()) {
                throw new IllegalStateException("未解析到任何题目");
            }
            // 统一补全请求指定类型(模型可能返回其他类型)
            for (QuestionPayload p : parsed) {
                if (p.type() == null && req.type() != null) {
                    // type 由调用方规整阶段统一赋值, 这里保持 null 以便格式校验按原样报告
                }
            }
            return parsed;
        } catch (IllegalStateException e) {
            throw BizException.bad("千帆返回内容格式不合法: " + e.getMessage());
        }
    }

    static final String SYSTEM_PROMPT = """
            你是资深的培训考核题库出题专家。请严格按照给定的要求生成高质量题目,
            只输出一个 JSON, 不要输出任何多余的文字、解释或 Markdown 代码块。
            JSON 结构为: {"questions": [题目对象...]}
            每个题目对象字段固定为:
            {
              "type": "SINGLE|MULTIPLE|JUDGE|SHORT",
              "stem": "题干文本",
              "options": ["选项A","选项B","选项C","选项D"],   // 选择题必填, 判断/简答缺省
              "answer": "答案",                                // 单选: 字母; 多选: 字母逗号分隔; 判断: 对/错; 简答: 参考答案
              "analysis": "解析(解释为什么选该项/得分要点)",
              "difficulty": 3                                   // 1~5 整数
            }
            硬性规则:
            1. 单选题: 4 个互不相同的选项, 恰 1 个正确; 答案写选项字母(如 "B")。
            2. 多选题: 4 个互不相同的选项, 其中 2~3 个正确; 答案写字母并用英文逗号分隔(如 "A,C")。
            3. 判断题: 题干为明确可判定的陈述; 答案只能是 "对" 或 "错"。
            4. 简答题: 给出包含得分要点的参考答案。
            5. 题干与解析要围绕用户给出的知识点展开, 内容准确、自洽, 避免空泛。
            6. 答案必须真实存在于选项中且唯一表述清晰, 严禁自相矛盾。
            """;

    private String buildUserPrompt(GenRequest req) {
        QuestionType t = req.type();
        StringBuilder sb = new StringBuilder();
        sb.append("【知识点】").append(req.knowledgePoint().getName());
        if (req.knowledgePoint().getCategory() != null) {
            sb.append("(分类: ").append(req.knowledgePoint().getCategory()).append(")");
        }
        if (req.knowledgePoint().getDescription() != null) {
            sb.append("; 知识点说明: ").append(req.knowledgePoint().getDescription());
        }
        sb.append("\n【题型】").append(t == null ? "四种题型混合配比" : t.getLabel());
        sb.append("\n【题目数量】").append(req.count()).append(" 道");
        sb.append("\n【难度系数】").append(req.difficulty()).append("(1 最易 ~ 5 最难)");
        if (req.extraInstruction() != null && !req.extraInstruction().isBlank()) {
            sb.append("\n【附加要求】").append(req.extraInstruction());
        }
        sb.append("\n请按上述 JSON 格式一次性返回 ").append(req.count()).append(" 道题目。");
        return sb.toString();
    }
}
