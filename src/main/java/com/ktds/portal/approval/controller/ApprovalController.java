package com.ktds.portal.approval.controller;

import com.ktds.portal.approval.domain.Approval;
import com.ktds.portal.approval.service.ApprovalService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * 결재 REST 컨트롤러.
 * [스멜] 컨트롤러가 Map 으로 파라미터를 받아 그대로 흘려보낸다(요청 DTO 부재, 검증 없음).
 * [스멜] 서비스의 매직넘버 action 을 그대로 노출한다.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @PostMapping
    public Approval create(@RequestBody Map<String, Object> body) {
        return service.create(
                (String) body.get("title"),
                (String) body.get("content"),
                (int) body.get("type"),       // type: 1=지출 2=휴가 3=구매 4=기타 — 요청자가 숫자 뜻을 외워야 함
                (int) body.get("priority"),   // priority: 1=낮음 2=보통 3=높음 — 잘못된 값(5 등)도 그냥 통과
                ((Number) body.get("drafterId")).longValue(),   // "drafterId"는 그냥 문자열이라 철자가 틀려도 컴파일은 됨 → 실행 때 null 로 터짐(DTO면 컴파일러가 즉시 잡아줌)
                ((Number) body.get("approverId")).longValue(),
                ((Number) body.getOrDefault("amount", 0)).longValue(),
                (boolean) body.getOrDefault("urgent", false)
        );
    }

    // action: 1=상신, 2=승인, 3=반려, 9=취소  ([스멜] 매직넘버를 API 가 그대로 강요)
    @PostMapping("/{id}/process")
    public void process(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.processApproval(
                id,
                ((Number) body.get("userId")).longValue(),
                (int) body.get("action"),     // 1=상신 2=승인 3=반려 9=취소 — 요청자(프론트엔드)가 숫자를 외워야 함
                (String) body.getOrDefault("reason", "")   // reason 키를 안 보내면 빈 문자열, 반려 사유 누락도 통과
        );
    }

    @GetMapping("/drafts/{userId}")
    public List<Approval> drafts(@PathVariable Long userId) {
        return service.myDrafts(userId);
    }

    @GetMapping("/inbox/{userId}")
    public List<Approval> inbox(@PathVariable Long userId) {
        return service.myInbox(userId);
    }
}
