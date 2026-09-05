package com.questionbank.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.questionbank.auth.UserContext;
import com.questionbank.common.BizException;
import com.questionbank.common.JsonUtils;
import com.questionbank.common.enums.QuestionSource;
import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.common.enums.QuestionType;
import com.questionbank.common.enums.ReviewAction;
import com.questionbank.dto.ImportDtos.ImportResult;
import com.questionbank.dto.ImportDtos.ImportRowResult;
import com.questionbank.dto.QuestionDtos.QuestionPayload;
import com.questionbank.entity.KnowledgePoint;
import com.questionbank.entity.Question;
import com.questionbank.entity.ReviewRecord;
import com.questionbank.repository.QuestionRepository;
import com.questionbank.repository.ReviewRecordRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题库批量导入: 支持 .xlsx / .json, 与 AI 生成共用同一套格式校验(QuestionFormat)。
 * 任意更换题库数据: 按模板(知识点/题型/题干/选项/答案/解析/难度)整理后导入即可。
 */
@Service
public class ImportService {

    private static final int MAX_ROWS = 5000;

    private final QuestionRepository questionRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final KnowledgePointService kpService;

    public ImportService(QuestionRepository questionRepository,
                         ReviewRecordRepository reviewRecordRepository,
                         KnowledgePointService kpService) {
        this.questionRepository = questionRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.kpService = kpService;
    }

    @Transactional
    public ImportResult importFile(MultipartFile file, QuestionStatus defaultStatus, String kpOverride) {
        if (file == null || file.isEmpty()) {
            throw BizException.bad("请选择要导入的文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        List<PendingRow> rows;
        try {
            if (name.endsWith(".json")) {
                rows = parseJson(new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8), kpOverride);
            } else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                rows = parseExcel(file, kpOverride);
            } else {
                throw BizException.bad("仅支持 .xlsx / .xls / .json 文件");
            }
        } catch (IOException e) {
            throw BizException.bad("文件读取失败: " + e.getMessage());
        }
        return doImport(name, rows, defaultStatus);
    }

    @Transactional
    public ImportResult importJsonText(String content, QuestionStatus defaultStatus) {
        if (!StringUtils.hasText(content)) {
            throw BizException.bad("JSON 内容为空");
        }
        return doImport("粘贴数据.json", parseJson(content, null), defaultStatus);
    }

    private ImportResult doImport(String fileName, List<PendingRow> rows, QuestionStatus status) {
        if (rows.isEmpty()) {
            throw BizException.bad("文件中没有可导入的数据行");
        }
        QuestionStatus target = status == null ? QuestionStatus.PUBLISHED : status;
        if (target != QuestionStatus.PUBLISHED && target != QuestionStatus.PENDING && target != QuestionStatus.DRAFT) {
            throw BizException.bad("导入后的默认状态仅支持: PUBLISHED(直接上架)/PENDING(待审核)/DRAFT(草稿)");
        }
        List<ImportRowResult> results = new ArrayList<>();
        int success = 0;
        int failed = 0;
        Map<String, Integer> stemSeen = new HashMap<>(); // 本次文件内查重
        for (PendingRow row : rows) {
            QuestionPayload p = QuestionFormat.normalize(row.payload());
            List<String> reasons = QuestionFormat.validate(p);
            if (reasons.isEmpty() && !StringUtils.hasText(p.knowledgePointName())) {
                reasons.add("缺少知识点");
            }
            if (reasons.isEmpty()) {
                Integer prior = stemSeen.get(p.stem());
                if (prior != null) {
                    reasons.add("与文件内第 " + prior + " 行题干重复");
                }
            }
            if (!reasons.isEmpty()) {
                failed++;
                results.add(new ImportRowResult(row.rowNo(), false, String.join("; ", reasons), null));
                continue;
            }
            if (questionRepository.findFirstByStem(p.stem()).isPresent()) {
                failed++;
                results.add(new ImportRowResult(row.rowNo(), false, "与库中已有题目题干重复", null));
                continue;
            }
            Question entity = buildEntity(p, target);
            entity = questionRepository.save(entity);
            if (target == QuestionStatus.PUBLISHED) {
                reviewRecordRepository.save(new ReviewRecord(entity.getId(), snapshot(entity), null,
                        UserContext.required().getName(), ReviewAction.IMPORT_PASS, "批量导入自动上架"));
            }
            stemSeen.put(p.stem(), row.rowNo());
            success++;
            results.add(new ImportRowResult(row.rowNo(), true, "导入成功", entity.getId()));
        }
        return new ImportResult(fileName, rows.size(), success, failed, target, results);
    }

    private String snapshot(Question q) {
        String stem = q.getStem();
        return stem.length() <= 300 ? stem : stem.substring(0, 300);
    }

    private Question buildEntity(QuestionPayload p, QuestionStatus status) {
        KnowledgePoint kp = kpService.getByNameOrNull(p.knowledgePointName());
        if (kp == null) {
            kp = kpService.create(new com.questionbank.dto.KnowledgePointDtos.KnowledgePointReq(
                    p.knowledgePointName(), "导入题库", "批量导入自动创建的知识点"));
        }
        Question q = new Question();
        q.setKnowledgePoint(kp);
        q.setType(p.type());
        q.setStem(p.stem());
        q.setOptionsJson(QuestionFormat.optionsToJson(p.options()));
        q.setAnswer(p.answer());
        q.setAnalysis(StringUtils.hasText(p.analysis()) ? p.analysis() : null);
        q.setDifficulty(p.difficulty());
        q.setSource(QuestionSource.IMPORT);
        q.setStatus(status);
        q.setCreatedBy(UserContext.required().getName());
        if (status == QuestionStatus.PUBLISHED) {
            q.setPublishedAt(java.time.LocalDateTime.now());
            q.setReviewerName(UserContext.required().getName());
        }
        return q;
    }

    // ---------------- 解析 ----------------

    private record PendingRow(int rowNo, QuestionPayload payload) {
    }

    private List<PendingRow> parseJson(String content, String kpOverride) {
        JsonNode root;
        try {
            root = JsonUtils.mapper().readTree(content);
        } catch (Exception e) {
            throw BizException.bad("JSON 内容解析失败: " + e.getMessage());
        }
        List<PendingRow> rows = new ArrayList<>();
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else {
            nodes.add(root);
        }
        int row = 0;
        for (JsonNode n : nodes) {
            row++;
            if (!n.isObject()) {
                rows.add(new PendingRow(row, null)); // null payload -> validation fail below
                continue;
            }
            QuestionPayload p = mapJsonNode(n, kpOverride);
            rows.add(new PendingRow(row, p));
        }
        return rows;
    }

    private QuestionPayload mapJsonNode(JsonNode n, String kpOverride) {
        QuestionType type = QuestionType.parse(text(n, "type", "题型"));
        String stem = text(n, "stem", "题干", "题目");
        String answer = text(n, "answer", "answers", "答案", "正确答案");
        String analysis = text(n, "analysis", "解析", "explanation");
        Integer difficulty = intVal(n, "difficulty", "难度");
        String kp = kpOverride != null ? kpOverride
                : firstNonNull(text(n, "knowledgePointName", "knowledgePoint", "知识点"), "");
        List<String> options = null;
        JsonNode opts = n.has("options") ? n.get("options") : null;
        if (opts != null && opts.isArray()) {
            List<String> list = new ArrayList<>();
            opts.forEach(o -> list.add(o.isTextual() ? o.asText() : o.toString()));
            options = list;
        } else if (opts != null && opts.isObject()) {
            List<String> list = new ArrayList<>();
            List<Map.Entry<String, JsonNode>> es = new ArrayList<>(opts.properties().size());
            opts.properties().forEach(es::add);
            es.sort(Map.Entry.comparingByKey());
            es.forEach(e -> list.add(e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString()));
            options = list;
        }
        return new QuestionPayload(type, stem, options, answer, analysis, difficulty, null,
                StringUtils.hasText(kp) ? kp : null);
    }

    private List<PendingRow> parseExcel(MultipartFile file, String kpOverride) throws IOException {
        List<PendingRow> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw BizException.bad("Excel 中没有工作表");
            }
            DataFormatter fmt = new DataFormatter();
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw BizException.bad("Excel 首行为空, 缺少表头");
            }
            // 列名 -> 语义
            Map<Integer, String> colSemantics = new LinkedHashMap<>();
            for (int c = headerRow.getFirstCellNum(); c <= headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                if (cell == null) {
                    continue;
                }
                String h = fmt.formatCellValue(cell).trim().toLowerCase();
                if (h.isEmpty()) {
                    continue;
                }
                colSemantics.put(c, classifyHeader(h));
            }
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String stem = null;
                String answer = null;
                String analysis = null;
                QuestionType type = null;
                String kp = kpOverride;
                Integer difficulty = null;
                List<String> options = new ArrayList<>();
                boolean any = false;
                Map<Integer, String> optionCells = new HashMap<>();
                for (Map.Entry<Integer, String> e : colSemantics.entrySet()) {
                    Cell cell = row.getCell(e.getKey());
                    String v = cell == null ? "" : fmt.formatCellValue(cell).trim();
                    if (v.isEmpty()) {
                        continue;
                    }
                    any = true;
                    switch (e.getValue()) {
                        case "stem" -> stem = v;
                        case "answer" -> answer = v;
                        case "analysis" -> analysis = v;
                        case "type" -> type = QuestionType.parse(v);
                        case "kp" -> kp = kp == null ? v : kp;
                        case "difficulty" -> difficulty = parseDifficulty(v);
                        default -> {
                            if (e.getValue().startsWith("opt")) {
                                int idx = Integer.parseInt(e.getValue().substring(3));
                                optionCells.put(idx, v);
                            }
                        }
                    }
                }
                if (!any) {
                    continue; // 空白行
                }
                int size = optionCells.keySet().stream().max(Integer::compareTo).orElse(-1) + 1;
                for (int i = 0; i < size; i++) {
                    options.add(optionCells.getOrDefault(i, ""));
                }
                rows.add(new PendingRow(r + 1, new QuestionPayload(type, stem, options, answer, analysis,
                        difficulty, null, StringUtils.hasText(kp) ? kp : null)));
            }
        }
        if (rows.size() > MAX_ROWS) {
            throw BizException.bad("单次导入不能超过 " + MAX_ROWS + " 行");
        }
        return rows;
    }

    private String classifyHeader(String h) {
        if (h.contains("知识点") || h.equals("kp") || h.contains("知识分类")) {
            return "kp";
        }
        if (h.contains("题型") || h.contains("类型") || h.equals("type")) {
            return "type";
        }
        if (h.contains("题干") || h.equals("题目") || h.equals("stem") || h.equals("question")) {
            return "stem";
        }
        if (h.contains("答案")) {
            return "answer";
        }
        if (h.contains("解析") || h.contains("分析") || h.equals("explanation") || h.equals("analysis")) {
            return "analysis";
        }
        if (h.contains("难度") || h.equals("difficulty")) {
            return "difficulty";
        }
        // 选项A / 选项1 / A / 1
        if (h.startsWith("选项") || h.startsWith("option")) {
            String letter = h.replace("选项", "").replace("option", "").trim().toUpperCase();
            int idx = letter.charAt(0) - 'A';
            if (idx >= 0 && idx < 8) {
                return "opt" + idx;
            }
            if (letter.chars().allMatch(Character::isDigit)) {
                int n = Integer.parseInt(letter) - 1;
                if (n >= 0 && n < 8) {
                    return "opt" + n;
                }
            }
        }
        if (h.matches("[a-h]")) {
            return "opt" + (h.charAt(0) - 'a');
        }
        if (h.matches("[1-8]")) {
            return "opt" + (Integer.parseInt(h) - 1);
        }
        return "ignore";
    }

    private Integer parseDifficulty(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(JsonNode n, String... names) {
        for (String name : names) {
            JsonNode v = n.get(name);
            if (v != null && v.isTextual()) {
                return v.asText();
            }
        }
        return null;
    }

    private Integer intVal(JsonNode n, String... names) {
        for (String name : names) {
            JsonNode v = n.get(name);
            if (v != null && v.isNumber()) {
                return v.asInt();
            }
        }
        return null;
    }

    private String firstNonNull(String a, String b) {
        return StringUtils.hasText(a) ? a : b;
    }

    // ---------------- 导入模板导出 ----------------

    public byte[] buildTemplateBytes() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("题目数据");
            String[] headers = {"知识点", "题型(单选/多选/判断/简答)", "题干", "选项A", "选项B", "选项C", "选项D",
                    "选项E", "选项F", "答案", "解析", "难度(1-5)"};
            CellStyle headStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headStyle.setFont(font);
            headStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = head.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headStyle);
                sheet.setColumnWidth(i, headers[i].getBytes(java.nio.charset.StandardCharsets.UTF_8).length * 300);
            }
            // 示例数据两行
            Object[][] samples = {
                    {"党史党建", "单选", "近代中国社会的性质是（ ）。",
                            "殖民地社会", "封建社会", "资本主义社会", "半殖民地半封建社会", "", "", "D",
                            "半殖民地半封建社会是中国近代社会的基本国情。", 3},
                    {"党史党建", "判断", "中国共产党成立于1921年7月。", "", "", "", "", "", "", "对",
                            "1921年7月中共一大召开, 标志中国共产党成立。", 2},
                    {"党史党建", "简答", "简述遵义会议的历史意义。", "", "", "", "", "", "",
                            "确立了毛泽东在党和红军中的领导地位, 是党的历史上生死攸关的转折点。", "要点: ①领导地位; ②转折点; ③独立自主解决自身问题。", 4},
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < samples[r].length; c++) {
                    Object val = samples[r][c];
                    if (val == null || val.toString().isEmpty()) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    if (val instanceof Integer i) {
                        cell.setCellValue(i);
                    } else {
                        cell.setCellValue(val.toString());
                    }
                }
            }
            // 说明表
            Sheet note = wb.createSheet("填写说明");
            String[] notes = {
                    "1. 第一行必须是表头, 请勿修改表头文字; 从第2行开始填题目。",
                    "2. 知识点: 系统中不存在的知识点导入时会自动创建。",
                    "3. 题型: 单选 / 多选 / 判断 / 简答。",
                    "4. 单选: 填选项A-D, 答案写一个字母(如 B); 至少2个选项。",
                    "5. 多选: 填选项A-F, 答案写字母逗号分隔(如 A,C); 至少2个正确项。",
                    "6. 判断: 不用填选项, 答案填 对 或 错。",
                    "7. 简答: 不用填选项, 答案填写参考答案, 解析可填写评分要点。",
                    "8. 难度: 1-5 整数, 缺省为 3。",
                    "9. 也可使用相同结构的 .json 文件导入: [{knowledgePoint,type,stem,options,answer,analysis,difficulty}]。",
                    "10. 导入默认状态可在上传时选择: 直接上架 / 待审核 / 草稿。"
            };
            for (int i = 0; i < notes.length; i++) {
                note.createRow(i).createCell(0).setCellValue(notes[i]);
                note.setColumnWidth(0, 120 * 256);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }
}
