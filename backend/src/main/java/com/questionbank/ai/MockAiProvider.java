package com.questionbank.ai;

import com.questionbank.common.enums.QuestionType;
import com.questionbank.dto.QuestionDtos.QuestionPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地模拟生成器: 未配置千帆 API Key 时的兜底演示源。
 * 生成的题目结构完全符合系统格式(会经过同一套格式校验), 内容明确标注为演示。
 */
public class MockAiProvider implements AiProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String label() {
        return "本地模拟生成(演示模式)";
    }

    @Override
    public boolean requiresApiKey() {
        return false;
    }

    @Override
    public List<QuestionPayload> generate(GenRequest req) {
        String kp = req.knowledgePoint().getName();
        List<QuestionPayload> out = new ArrayList<>();
        int count = Math.min(Math.max(req.count(), 1), 30);
        int difficulty = req.difficulty();
        for (int i = 0; i < count; i++) {
            out.add(buildOne(req.type(), kp, i + 1, difficulty));
        }
        return out;
    }

    private QuestionPayload buildOne(QuestionType type, String kp, int i, int difficulty) {
        String tag = "【演示题·" + i + "】";
        QuestionType t = type != null ? type : QuestionType.values()[i % QuestionType.values().length];
        if (t == null) {
            t = QuestionType.SINGLE;
        }
        String no = i == 0 ? "一" : i == 1 ? "二" : i == 2 ? "三" : String.valueOf(i);
        String kpRef = "知识点「" + kp + "」";
        return switch (t) {
            case SINGLE -> new QuestionPayload(t,
                    tag + "关于" + kpRef + "的相关内容，下列说法正确的是（ ）。",
                    List.of("本选项为第" + no + "个演示选项, 表述与该知识点相吻合",
                            "本选项为第" + no + "个演示选项, 表述与该知识点部分相悖",
                            "本选项为第" + no + "个演示选项, 表述与该知识点无关",
                            "本选项为第" + no + "个演示选项, 表述明显错误"),
                    i % 2 == 0 ? "A" : "B",
                    "【模拟解析】本题围绕" + kpRef + "设计, 用于演示出题-校验-审核-上架全流程。",
                    difficulty, null, null);
            case MULTIPLE -> new QuestionPayload(t,
                    tag + "关于" + kpRef + "，下列属于其培训考核方向的选项有（多选）。",
                    List.of("方向A(第" + no + "个演示方向, 相关)",
                            "方向B(第" + no + "个演示方向, 不相关)",
                            "方向C(第" + no + "个演示方向, 相关)",
                            "方向D(第" + no + "个演示方向, 不相关)"),
                    "A,C",
                    "【模拟解析】本题围绕" + kpRef + "的多个考核方向设计。",
                    difficulty, null, null);
            case JUDGE -> new QuestionPayload(t,
                    tag + "判断题: 题库系统中存在名为「" + kp + "」的知识点, 可作为自动出题依据。",
                    null,
                    i % 2 == 0 ? "对" : "错",
                    "【模拟解析】题干直接声明知识点名称, 与系统中已登记的知识点一致/不一致。",
                    difficulty, null, null);
            case SHORT -> new QuestionPayload(t,
                    tag + "简答题: 请简要阐述" + kpRef + "的主要内容, 并说明其在培训考核中的典型应用。",
                    null,
                    "【模拟参考答案】" + kpRef + "是培训题库的核心知识点之一。可从定义、要点、应用场景三个层面作答, 并结合实际案例展开说明。",
                    "【模拟解析】评分要点: ①能准确描述知识点内涵; ②能结合培训场景举例; ③条理清晰、表述完整。",
                    difficulty, null, null);
        };
    }
}
