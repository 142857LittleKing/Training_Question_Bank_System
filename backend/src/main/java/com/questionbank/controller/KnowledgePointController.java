package com.questionbank.controller;

import com.questionbank.common.ApiResponse;
import com.questionbank.common.PageResult;
import com.questionbank.common.enums.UserRole;
import com.questionbank.dto.KnowledgePointDtos.KnowledgePointReq;
import com.questionbank.dto.KnowledgePointDtos.KnowledgePointView;
import com.questionbank.entity.KnowledgePoint;
import com.questionbank.service.KnowledgePointService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-points")
public class KnowledgePointController {

    private final KnowledgePointService kpService;

    public KnowledgePointController(KnowledgePointService kpService) {
        this.kpService = kpService;
    }

    /** 分页列表 */
    @GetMapping
    public ApiResponse<PageResult<KnowledgePointView>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(kpService.page(keyword, page, size));
    }

    /** 全量列表(下拉选择用, 附带题目数) */
    @GetMapping("/all")
    public ApiResponse<List<KnowledgePointView>> all() {
        return ApiResponse.ok(kpService.listAll().stream().map(kpService::toView).toList());
    }

    @PostMapping
    public ApiResponse<KnowledgePoint> create(@Valid @RequestBody KnowledgePointReq req) {
        UserRoleHolder.requireEdit();
        return ApiResponse.ok(kpService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgePoint> update(@PathVariable Long id, @Valid @RequestBody KnowledgePointReq req) {
        UserRoleHolder.requireEdit();
        return ApiResponse.ok(kpService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        UserRoleHolder.requireEdit();
        kpService.delete(id);
        return ApiResponse.ok();
    }

    /** 简单角色守卫(避免在各 Service 里重复判断) */
    static final class UserRoleHolder {
        static void requireEdit() {
            com.questionbank.auth.UserContext.requireRole(UserRole.GENERATOR, UserRole.ADMIN);
        }
    }
}
