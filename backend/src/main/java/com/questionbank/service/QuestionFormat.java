package com.questionbank.service;

import com.questionbank.common.JsonUtils;
import com.questionbank.common.enums.QuestionType;
import com.questionbank.dto.QuestionDtos.QuestionPayload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 题目格式统一校验与规整中心。
 *
 * AI 出题结果 / 手工录入 / 批量导入 的题目都经过这里校验,
 * 保证"出题结果格式校验通过"这一验收标准有统一的实现与回显。
 */
public final class QuestionFormat {

    public static final int MAX_STEM = 3000;
    public static final int MAX_ANSWER = 2000;
    public static final int MAX_OPTION_TEXT = 500;
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 8;

    private static final Pattern OPTION_PREFIX = Pattern.compile("^[A-Ha-h]\\s*[、.．:：]\\s*");
    private static final Pattern LETTERS_SPLIT = Pattern.compile("[,，;；、\\s]+");

    private QuestionFormat() {
    }

    /** 规整(修复琐碎问题): 去空格/选项字母前缀/判断题同义词/多选排序/难度取值 */
    public static QuestionPayload normalize(QuestionPayload p) {
        if (p == null) {
            return null;
        }
        QuestionType type = p.type();
        String stem = trim(p.stem());
        String answer = trim(p.answer());
        String analysis = trim(p.analysis());
        Integer difficulty = p.difficulty() == null ? 3 : Math.max(1, Math.min(5, p.difficulty()));
        String kpName = trim(p.knowledgePointName());

        List<String> options = null;
        if (type != null && type.isHasOptions()) {
            options = new ArrayList<>();
            if (p.options() != null) {
                for (String o : p.options()) {
                    if (o == null) {
                        options.add("");
                        continue;
                    }
                    String t = OPTION_PREFIX.matcher(o).replaceFirst("").trim();
                    options.add(t);
                }
            }
            // 去除尾部空白选项
            while (!options.isEmpty() && options.get(options.size() - 1).isBlank()) {
                options.remove(options.size() - 1);
            }
        }

        if (type == QuestionType.JUDGE) {
            answer = normalizeJudgeAnswer(answer);
        } else if (type == QuestionType.MULTIPLE) {
            answer = normalizeMultipleAnswer(answer);
        } else if (type == QuestionType.SINGLE) {
            answer = answer.toUpperCase().replaceAll("\\s+", "");
        }

        return new QuestionPayload(type, stem, options, answer, analysis, difficulty,
                p.knowledgePointId(), kpName);
    }

    private static String normalizeJudgeAnswer(String a) {
        if (a == null) {
            return "";
        }
        String t = a.trim().toUpperCase();
        if (t.equals("对") || t.equals("正确") || t.equals("TRUE") || t.equals("T") || t.equals("√")
                || t.equals("YES") || t.equals("1") || t.equals("A")) {
            return "对";
        }
        if (t.equals("错") || t.equals("错误") || t.equals("FALSE") || t.equals("F") || t.equals("×")
                || t.equals("X") || t.equals("NO") || t.equals("0") || t.equals("B")) {
            return "错";
        }
        return a.trim();
    }

    private static String normalizeMultipleAnswer(String a) {
        if (a == null) {
            return "";
        }
        Set<String> letters = new LinkedHashSet<>();
        for (String part : LETTERS_SPLIT.split(a.trim().toUpperCase())) {
            String t = part.trim();
            if (!t.isEmpty() && t.length() <= 2) {
                letters.add(t);
            }
        }
        return String.join(",", letters);
    }

    /** 返回校验问题列表; 空列表 = 通过 */
    public static List<String> validate(QuestionPayload raw) {
        QuestionPayload p = raw == null ? null : normalize(raw);
        List<String> reasons = new ArrayList<>();
        if (p == null || p.type() == null) {
            reasons.add("题型不能为空");
            return reasons;
        }
        QuestionType type = p.type();
        if (p.stem().isEmpty()) {
            reasons.add("题干不能为空");
        } else if (p.stem().length() > MAX_STEM) {
            reasons.add("题干超过 " + MAX_STEM + " 字符");
        }

        if (type.isHasOptions()) {
            List<String> options = p.options();
            if (options == null || options.isEmpty()) {
                reasons.add("选择题必须提供选项");
            } else {
                if (options.size() < MIN_OPTIONS) {
                    reasons.add("选项数量不足(" + type.getLabel() + "至少 " + MIN_OPTIONS + " 项)");
                }
                if (options.size() > MAX_OPTIONS) {
                    reasons.add("选项数量超过上限 " + MAX_OPTIONS + " 项");
                }
                int idx = 0;
                for (String o : options) {
                    if (o == null || o.isBlank()) {
                        reasons.add("选项 " + (char) ('A' + Math.min(idx, 25)) + " 为空");
                    } else if (o.length() > MAX_OPTION_TEXT) {
                        reasons.add("选项 " + (char) ('A' + Math.min(idx, 25)) + " 超过 " + MAX_OPTION_TEXT + " 字符");
                    }
                    idx++;
                }
            }
        }

        switch (type) {
            case SINGLE -> {
                if (p.answer().length() != 1 || !Character.isLetter(p.answer().charAt(0))) {
                    reasons.add("单选答案应为单个选项字母(如 A/B/C/D)");
                } else {
                    int idx = p.answer().charAt(0) - 'A';
                    if (p.options() == null || idx < 0 || idx >= p.options().size()) {
                        reasons.add("单选答案 " + p.answer() + " 不在选项范围内");
                    }
                }
            }
            case MULTIPLE -> {
                if (p.answer().isEmpty()) {
                    reasons.add("多选答案不能为空");
                } else {
                    List<String> parts = Arrays.asList(p.answer().split(","));
                    if (parts.size() < 2) {
                        reasons.add("多选题应至少包含 2 个正确选项");
                    }
                    for (String part : parts) {
                        if (part.length() != 1 || !Character.isLetter(part.charAt(0))) {
                            reasons.add("多选答案格式错误: " + part + " (应为逗号分隔的选项字母)");
                        } else {
                            int idx = part.charAt(0) - 'A';
                            if (p.options() == null || idx < 0 || idx >= p.options().size()) {
                                reasons.add("多选答案 " + part + " 不在选项范围内");
                            }
                        }
                    }
                }
            }
            case JUDGE -> {
                if (!p.answer().equals("对") && !p.answer().equals("错")) {
                    reasons.add("判断题答案必须为\"对\"或\"错\"(收到: " + (p.answer().isEmpty() ? "空" : p.answer()) + ")");
                }
            }
            case SHORT -> {
                if (p.answer().isEmpty()) {
                    reasons.add("简答题参考答案不能为空");
                } else if (p.answer().length() > MAX_ANSWER) {
                    reasons.add("简答答案超过 " + MAX_ANSWER + " 字符");
                }
            }
        }

        if (p.analysis() != null && p.analysis().length() > 3000) {
            reasons.add("解析超过 3000 字符");
        }
        return reasons;
    }

    /** 校验通过与否 */
    public static boolean isValid(QuestionPayload p) {
        return validate(p).isEmpty();
    }

    /** 选项列表 -> DB 存储 JSON; 无选项返回 null */
    public static String optionsToJson(List<String> options) {
        if (options == null) {
            return null;
        }
        return JsonUtils.toJson(options.stream()
                .map(o -> o == null ? "" : o)
                .collect(Collectors.toList()));
    }

    /** DB 存储 JSON -> 选项列表 */
    public static List<String> optionsFromJson(String json) {
        return JsonUtils.parseList(json, String.class);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
