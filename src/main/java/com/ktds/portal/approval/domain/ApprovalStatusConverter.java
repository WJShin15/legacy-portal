package com.ktds.portal.approval.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] ApprovalStatus enum ↔ DB 정수 코드(0/1/2/3/9) 매핑.
 * DB 저장값은 기존과 동일한 정수 그대로 유지한다(CLAUDE.md 불변 규칙: DB 저장값 변경 금지).
 * autoApply=false — Approval.status 필드에서만 @Convert로 명시 적용한다.
 */
@Converter(autoApply = false)
public class ApprovalStatusConverter implements AttributeConverter<ApprovalStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ApprovalStatus status) {
        return status == null ? null : status.getCode();
    }

    @Override
    public ApprovalStatus convertToEntityAttribute(Integer code) {
        return code == null ? null : ApprovalStatus.fromCode(code);
    }
}
