package com.questionbank.ai;

import com.questionbank.common.enums.QuestionType;
import com.questionbank.dto.QuestionDtos.QuestionPayload;
import com.questionbank.entity.KnowledgePoint;

import java.util.List;

/**
 * 出题源抽象: 百度千帆大模型 or 本地模拟器。
 * 模拟器保证"未配置 API Key 也能完整体验 出题->校验->审核->上架"流程。
 */
public interface AiProvider {

    /** 标识: qianfan / mock */
    String name();

    /** 展示名 */
    String label();

    /** 是否需要用户配置 API Key */
    boolean requiresApiKey();

    /** 生成 count 道 type 类型题目(未做格式校验, 由调用方统一校验) */
    List<QuestionPayload> generate(GenRequest req);

    /** 生成参数 */
    record GenRequest(KnowledgePoint knowledgePoint, QuestionType type, int count,
                      int difficulty, String extraInstruction) {
    }

    /** 出题源信息(供前端展示当前模式) */
    record ProviderInfo(String name, String label, boolean requiresApiKey,
                        boolean apiKeyConfigured, String model, String modeTip) {
    }
}
