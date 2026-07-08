package com.ktds.portal.approval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * [리팩토링] 레거시 Approval.status의 매직넘버(int: 0/1/2/3/9) → 여기서 enum으로
 * (Primitive Obsession 제거 · Replace Magic Number with Symbolic Constant, docs/4-8 참고)
 *
 * - DB 저장값(불변 규칙 보존): {@link ApprovalStatusConverter}(@Convert)가 이 enum과 기존
 *   정수 코드를 그대로 매핑한다. @Enumerated(STRING)·@Enumerated(ORDINAL)은 쓰지 않는다
 *   (문자로 저장되거나, 선언 순서가 9와 어긋나 버리기 때문).
 * - JSON 응답 형식(불변 규칙 보존): ApprovalController가 Approval 엔티티를 그대로 반환하므로
 *   (docs/4-4의 "Entity 직접 API 노출" 참고), @JsonValue로 getCode()를 노출해 Jackson이
 *   기존과 동일하게 정수로 직렬화하도록 한다. 이 애노테이션이 없으면 기본 직렬화가
 *   "SUBMITTED" 같은 문자열이 되어 응답 구조가 바뀌어 버린다.
 */
public enum ApprovalStatus {
    DRAFT(0),       // 임시저장
    SUBMITTED(1),   // 상신
    APPROVED(2),    // 승인
    REJECTED(3),    // 반려
    CANCELED(9);    // 취소 (4~8은 레거시에서도 정의된 적 없는 결번 — 그대로 비워둔다)

    private final int code;

    ApprovalStatus(int code) {
        this.code = code;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    @JsonCreator
    public static ApprovalStatus fromCode(int code) {
        for (ApprovalStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown Approval status code: " + code);
    }
}
