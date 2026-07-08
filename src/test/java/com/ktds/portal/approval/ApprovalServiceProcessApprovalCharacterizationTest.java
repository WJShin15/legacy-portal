package com.ktds.portal.approval;

import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * [특성화 테스트] processApproval(id, userId, action, reason)의 현재 동작을 '있는 그대로' 고정한다.
 *
 * 목적은 옳고 그름의 판단이 아니라 "레거시가 지금 이렇게 동작한다"는 사실 고정이다(CLAUDE.md 원칙).
 * 리팩토링 전/후 이 6개 테스트가 변경 없이 그대로 green이어야 리팩토링이 안전하게 끝난 것이다.
 *
 * status 관찰값: 0=임시저장 1=상신 2=승인 3=반려 9=취소 (현재 코드의 매직넘버 그대로 사용 — enum 미도입 상태)
 * action 값:     1=상신   2=승인 3=반려 9=취소
 *
 * @DataJpaTest로 레포지토리/엔티티 슬라이스만 띄우고, ApprovalService는 이 슬라이스에 없으므로
 * @Import로 빈 등록 후 @Autowired로 주입받는다(테스트 코드에서 new ApprovalService(...) 직접 생성 금지).
 * 테스트 데이터는 반드시 service.create()로 만들어 실제 서비스 계약을 그대로 통과시킨다.
 */
@DataJpaTest
@Import(ApprovalService.class)
class ApprovalServiceProcessApprovalCharacterizationTest {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    private User 김사원_기안자;
    private User 박팀장_결재자;
    private User 최사원_권한없는결재자;

    @BeforeEach
    void 사용자_준비() {
        김사원_기안자 = userRepository.save(new User("김사원", "kim@ktds.com", 1, "개발1팀"));       // role 1=사원
        박팀장_결재자 = userRepository.save(new User("박팀장", "park@ktds.com", 2, "개발1팀"));       // role 2=팀장 (승인권한 있음)
        최사원_권한없는결재자 = userRepository.save(new User("최사원", "choi@ktds.com", 1, "개발2팀")); // role 1=사원 (승인권한 없음)
    }

    // 1) 상신 → 승인
    @Test
    void 상신후_승인하면_상태가_승인으로_바뀐다() {
        Approval 결재 = approvalService.create(
                "노트북 구매", "개발용 노트북", 1, 2,
                김사원_기안자.getId(), 박팀장_결재자.getId(), 500_000L, false);

        approvalService.processApproval(결재.getId(), 김사원_기안자.getId(), 1, ""); // action 1=상신
        approvalService.processApproval(결재.getId(), 박팀장_결재자.getId(), 2, ""); // action 2=승인

        Approval 결과 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(결과.getStatus()).isEqualTo(2); // status 2=승인(APPROVED)
    }

    // 2) 반려
    @Test
    void 상신후_반려하면_상태가_반려로_바뀌고_반려사유가_저장된다() {
        Approval 결재 = approvalService.create(
                "연차 사용", "개인 사유", 2, 1,
                김사원_기안자.getId(), 박팀장_결재자.getId(), 0L, false);

        approvalService.processApproval(결재.getId(), 김사원_기안자.getId(), 1, "");            // action 1=상신
        approvalService.processApproval(결재.getId(), 박팀장_결재자.getId(), 3, "일정 겹침");    // action 3=반려

        Approval 결과 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(결과.getStatus()).isEqualTo(3);              // status 3=반려(REJECTED)
        assertThat(결과.getRejectReason()).isEqualTo("일정 겹침");
    }

    // 3) 취소
    @Test
    void 임시저장_상태에서_기안자가_취소하면_상태가_취소로_바뀐다() {
        Approval 결재 = approvalService.create(
                "사무용품 구매", "볼펜 등", 3, 1,
                김사원_기안자.getId(), 박팀장_결재자.getId(), 30_000L, false);

        approvalService.processApproval(결재.getId(), 김사원_기안자.getId(), 9, ""); // action 9=취소

        Approval 결과 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(결과.getStatus()).isEqualTo(9); // status 9=취소(CANCELED)
    }

    // 4) 권한 없는 승인 — CLAUDE.md가 명시한 "권한 없는 승인을 조용히 무시"하는 레거시 결함을 그대로 고정
    @Test
    void 권한없는_사용자가_승인을_시도하면_조용히_무시되고_상태가_그대로_유지된다() {
        // 결재자로 지정된 최사원의 role=1(사원)이라 승인 권한(role>=2)이 없다.
        Approval 결재 = approvalService.create(
                "출장비 정산", "지방 출장", 1, 2,
                김사원_기안자.getId(), 최사원_권한없는결재자.getId(), 200_000L, false);

        approvalService.processApproval(결재.getId(), 김사원_기안자.getId(), 1, "");             // action 1=상신
        approvalService.processApproval(결재.getId(), 최사원_권한없는결재자.getId(), 2, "");      // action 2=승인 시도(권한 없음)

        Approval 결과 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(결과.getStatus()).isEqualTo(1); // 예외 없이 조용히 무시 — 여전히 1=상신(SUBMITTED)
    }

    // 5) 없는 id
    @Test
    void 존재하지_않는_id로_처리를_요청하면_조용히_무시된다() {
        Long 존재하지않는id = 999_999L;

        assertDoesNotThrow(() ->
                approvalService.processApproval(존재하지않는id, 박팀장_결재자.getId(), 2, ""));

        assertThat(approvalRepository.findById(존재하지않는id)).isEmpty(); // 예외 없이 조용히 무시, 아무 것도 생성되지 않음
    }

    // 6) 재승인 — 이미 승인(2)된 건은 상신(1) 상태가 아니므로 재요청해도 아무 효과가 없다
    @Test
    void 이미_승인된_결재를_다시_승인_요청해도_상태가_그대로_유지된다() {
        Approval 결재 = approvalService.create(
                "회의실 비품", "화이트보드", 3, 1,
                김사원_기안자.getId(), 박팀장_결재자.getId(), 50_000L, false);

        approvalService.processApproval(결재.getId(), 김사원_기안자.getId(), 1, ""); // action 1=상신
        approvalService.processApproval(결재.getId(), 박팀장_결재자.getId(), 2, ""); // action 2=1차 승인
        approvalService.processApproval(결재.getId(), 박팀장_결재자.getId(), 2, ""); // action 2=재승인 시도

        Approval 결과 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(결과.getStatus()).isEqualTo(2); // 변화 없음 — 여전히 2=승인(APPROVED)
    }
}
