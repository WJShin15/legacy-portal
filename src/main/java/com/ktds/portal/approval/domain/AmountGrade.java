package com.ktds.portal.approval.domain;

/**
 * [리팩토링] ApprovalService.amountGrade(Approval)을 Move Method로 이전.
 * 금액을 등급으로 분류하는 규칙은 서비스가 아니라 금액 자체에 대한 도메인 규칙이므로
 * 여기로 옮긴다(Feature Envy 제거, docs/4-4 스멜 #6). 등급 기준(1000만원=S, 100만원=A,
 * 10만원=B, 그 미만=C)은 레거시와 완전히 동일 — 값을 바꾸지 않는다.
 */
public enum AmountGrade {
    S(10_000_000L),  // 1000만원 이상
    A(1_000_000L),   // 100만원 이상
    B(100_000L),     // 10만원 이상
    C(0L);           // 그 미만 전부

    private final long threshold;

    AmountGrade(long threshold) {
        this.threshold = threshold;
    }

    public static AmountGrade of(long amount) {
        if (amount >= S.threshold) return S;
        if (amount >= A.threshold) return A;
        if (amount >= B.threshold) return B;
        return C;
    }
}
