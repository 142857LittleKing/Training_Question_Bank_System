package com.questionbank.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.questionbank.common.JsonUtils;
import com.questionbank.dto.QuestionDtos.QuestionPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把大模型返回的文本解析成题目载荷。
 * 兼容: ```json 代码块包裹 / 顶层 questions 数组 / 顶层单对象 / 顶层数组;
 * 字段兼容中英文: stem/题干/question, options/选项, answer/answers/答案, analysis/解析, difficulty/难度。
 */
public final class AiResponseParser {

    private AiResponseParser() {
    }

    public static List<QuestionPayload> parse(String rawContent) throws IllegalStateException {
        String text = rawContent == null ? "" : rawContent.trim();
        // 去掉 ```json ... ``` 代码块
        text = text.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replaceAll("```", "");
        JsonNode root = extractJson(text);
        if (root == null) {
            String snippet = text.length() > 300 ? text.substring(0, 300) : text;
            throw new IllegalStateException("未能从模型返回内容中解析出 JSON: " + snippet);
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else if (root.has("questions") && root.get("questions").isArray()) {
            root.get("questions").forEach(nodes::add);
        } else if (root.has("data") && root.get("data").isArray()) {
            root.get("data").forEach(nodes::add);
        } else {
            nodes.add(root);
        }
        List<QuestionPayload> out = new ArrayList<>();
        for (JsonNode n : nodes) {
            QuestionPayload p = mapNode(n);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    private static JsonNode extractJson(String text) {
        if (text.isEmpty()) {
            return null;
        }
        char first = text.charAt(0);
        int start = first == '{' || first == '[' ? 0 : text.indexOf(first == '{' ? '{' : '[');
        // 尝试 JSON 原文
        JsonNode direct = JsonUtils.readTree(text);
        if (direct != null) {
            return direct;
        }
        if (start < 0) {
            return null;
        }
        // 找平衡括号的尾部
        char open = text.charAt(start);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inStr = false;
        char esc = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inStr) {
                if (esc == '\\') {
                    esc = 0;
                } else if (c == '\\') {
                    esc = '\\';
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return JsonUtils.readTree(text.substring(start, i + 1));
                }
            }
        }
        return null;
    }

    private static QuestionPayload mapNode(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        String stem = firstText(n, "stem", "题干", "question", "题目", "题目内容", "content");
        JsonNode optNode = first(n, "options", "选项", "optionList");
        List<String> options = null;
        if (optNode != null) {
            if (optNode.isArray()) {
                options = new ArrayList<>();
                for (JsonNode o : optNode) {
                    if (o.isTextual()) {
                        options.add(o.asText());
                    } else if (o.isObject()) {
                        // {"A": "文本"} 形式按 key 排序
                        List<Map.Entry<String, JsonNode>> es = new ArrayList<>();
                        o.properties().forEach(es::add);
                        es.sort((a, b) -> a.getKey().compareTo(b.getKey()));
                        for (Map.Entry<String, JsonNode> e : es) {
                            options.add(e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString());
                        }
                    }
                }
            } else if (optNode.isTextual()) {
                // 按换行/分号切分
                for (String line : optNode.asText().split("[\\n;；]")) {
                    String t = line.trim();
                    if (!t.isEmpty()) {
                        options.add(t);
                    }
                }
            }
        }
        String answer = null;
        JsonNode ansNode = first(n, "answer", "answers", "答案", "答案选项", "正确答案");
        if (ansNode != null) {
            if (ansNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode a : ansNode) {
                    if (a.isTextual()) {
                        sb.append(a.asText()).append(",");
                    }
                }
                answer = sb.length() > 0 ? sb.substring(0, sb.length() - 1) : null;
            } else {
                answer = ansNode.isTextual() ? ansNode.asText() : ansNode.toString();
            }
        }
        String analysis = firstText(n, "analysis", "解析", "explanation", "详解");
        Integer difficulty = null;
        JsonNode diffNode = first(n, "difficulty", "难度");
        if (diffNode != null && diffNode.isNumber()) {
            difficulty = diffNode.asInt();
        }
        if (stem == null || stem.isBlank()) {
            return null;
        }
        if ((options == null || options.isEmpty()) && answer == null) {
            return null;
        }
        return new QuestionPayload(null, stem.trim(), options, answer == null ? "" : answer.trim(),
                analysis == null ? null : analysis.trim(), difficulty, null, null);
    }

    private static JsonNode first(JsonNode n, String... names) {
        for (String name : names) {
            JsonNode v = n.get(name);
            if (v != null && !v.isNull()) {
                return v;
            }
        }
        return null;
    }

    private static String firstText(JsonNode n, String... names) {
        JsonNode v = first(n, names);
        if (v == null) {
            return null;
        }
        return v.isTextual() ? v.asText() : v.toString();
    }
}
