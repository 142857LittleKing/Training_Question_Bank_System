package com.questionbank.controller;

import com.questionbank.common.ApiResponse;
import com.questionbank.common.BizException;
import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.dto.ImportDtos.ImportJsonReq;
import com.questionbank.dto.ImportDtos.ImportResult;
import com.questionbank.service.ImportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** 题库批量导入(支持任意更换新题库: .xlsx / .json) */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /** 下载导入模板 */
    @GetMapping("/template")
    public ResponseEntity<ByteArrayResource> template() throws IOException {
        byte[] bytes = importService.buildTemplateBytes();
        ByteArrayResource res = new ByteArrayResource(bytes);
        String filename = "题库导入模板.xlsx";
        String encoded = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(res);
    }

    /** 上传 .xlsx / .json 文件导入 */
    @PostMapping
    public ApiResponse<ImportResult> upload(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "defaultStatus", required = false) QuestionStatus defaultStatus,
                                            @RequestParam(value = "kpName", required = false) String kpName) {
        return ApiResponse.ok(importService.importFile(file, defaultStatus, kpName));
    }

    /** 直接粘贴 JSON 导入 */
    @PostMapping("/json")
    public ApiResponse<ImportResult> json(@RequestBody ImportJsonReq req) {
        QuestionStatus status = req.defaultStatus();
        return ApiResponse.ok(importService.importJsonText(req.content(), status));
    }
}
