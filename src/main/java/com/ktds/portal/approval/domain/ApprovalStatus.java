package com.ktds.portal.approval.domain;

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
 *
 * [리팩토링] label() — ApprovalService.statusLabel(Approval)을 Move Method로 이전.
 *   상태를 화면 표시 문자열로 번역하는 규칙은 상태 자신이 아는 게 자연스럽다(Feature Envy 제거,
 *   docs/4-4 스멜 #6). 레거시의 "알수없음" 폴백은 enum 도입 이후 이미 도달 불가능했던 분기라
 *   옮기지 않는다(각 상수가 자신의 라벨을 반드시 가지므로 폴백 자체가 성립하지 않음).
 */
public enum ApprovalStatus {
    DRAFT(0, "임시저장"),
    SUBMITTED(1, "상신"),
    APPROVED(2, "승인"),
    REJECTED(3, "반려"),
    CANCELED(9, "취소");    // 4~8은 레거시에서도 정의된 적 없는 결번 — 그대로 비워둔다

    private final int code;
    private final String label;

    ApprovalStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String label() {
        return label;
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
