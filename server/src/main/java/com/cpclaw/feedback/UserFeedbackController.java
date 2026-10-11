package com.cpclaw.feedback;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.identity.PrincipalContextService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/feedback")
public class UserFeedbackController {
    private final UserFeedbackRepository repository;
    private final PrincipalContextService principals;
    public UserFeedbackController(UserFeedbackRepository repository, PrincipalContextService principals){this.repository=repository;this.principals=principals;}
    @PostMapping
    public ApiResponse<UserFeedback> create(@RequestBody CreateFeedback request){
        if (request == null || blank(request.title()) || blank(request.content())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "反馈标题和内容不能为空");
        }
        if (request.title().length() > 200 || request.content().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "反馈标题或内容超出长度限制");
        }
        if (!List.of("BUG", "SUGGESTION", "DATA", "OTHER").contains(request.category())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "反馈类型不受支持");
        }
        var p=principals.current(); var now=Instant.now(); var item=new UserFeedback();
        item.setId(UUID.randomUUID().toString()); item.setTenantId(p.tenantId()); item.setSubjectId(p.principalId());
        item.setAppCode(request.appCode()); item.setCategory(request.category()); item.setTitle(request.title()); item.setContent(request.content()); item.setStatus("OPEN"); item.setCreatedAt(now); item.setUpdatedAt(now);
        return ApiResponse.ok(repository.save(item));
    }
    @GetMapping
    public ApiResponse<List<UserFeedback>> list(){
        if (!principals.current().superAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有运营管理员可以查看反馈列表");
        return ApiResponse.ok(repository.findAllByTenantIdOrderByCreatedAtDesc(principals.current().tenantId()));
    }
    @PutMapping("/{id}")
    public ApiResponse<UserFeedback> update(@PathVariable String id, @RequestBody UpdateFeedback request) {
        var principal = principals.current();
        if (!principal.superAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有运营管理员可以处理反馈");
        if (request == null || !List.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "反馈状态不受支持");
        }
        var item = repository.findByIdAndTenantId(id, principal.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "反馈不存在"));
        item.setStatus(request.status());
        if (request.adminNote() != null) item.setAdminNote(request.adminNote().substring(0, Math.min(2000, request.adminNote().length())));
        item.setHandledBy(principal.principalId());
        item.setUpdatedAt(Instant.now());
        return ApiResponse.ok(repository.save(item));
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    public record CreateFeedback(String appCode,String category,String title,String content){}
    public record UpdateFeedback(String status, String adminNote){}
}
