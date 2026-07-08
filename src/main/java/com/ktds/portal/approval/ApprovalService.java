package com.ktds.portal.approval;

import com.ktds.portal.common.FileAuditLogger;
import com.ktds.portal.common.SmtpMailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 결재 서비스 — 이 클래스가 이 과정의 "주인공 안티패턴"이다.
 *
 * ============================ 의도적으로 심어둔 스멜 목록 ============================
 *  1. God Class            : 검증 + 영속화 + 메일 + 감사로그 + 포맷팅 + 권한판정을 혼자 다 한다.
 *  2. Long Method          : processApproval() 한 메서드가 100줄 이상, 중첩 if 6단계.
 *  3. Magic Number         : status 0/1/2/3/9, type 1~4, role 1~3, priority 1~3 이 흩어져 있다.
 *  4. Duplicated Code      : 메일 본문 생성/감사 로그 기록이 메서드마다 복붙 되어 있다.
 *  5. Tight Coupling       : new SmtpMailSender(), new FileAuditLogger() 직접 생성(DI 없음).
 *  6. Feature Envy         : Approval 의 필드를 꺼내 서비스가 직접 상태/금액 규칙을 계산한다.
 *  7. Primitive Obsession  : 모든 분기를 int 비교로 처리한다.
 *  8. Long Parameter List  : create() 파라미터 8개.
 *  9. Poor Naming          : d, u, proc, tmp, flag1 같은 약어.
 * 10. Comment Smell        : 나쁜 이름을 주석으로 변명한다.
 * 11. No Tests             : 테스트가 단 한 개도 없다(안전망 부재).
 * =================================================================================
 *
 * [리팩토링 진행] status(0/1/2/3/9)는 ApprovalStatus enum으로 전환 완료(docs/4-11 BL-4).
 *                 DB엔 ApprovalStatusConverter가 정수 그대로 저장. type/priority/role/action은
 *                 아직 int로 남아있다(다음 대상).
 */
@Service
public class ApprovalService {

    private final ApprovalRepository repo;
    private final UserRepository userRepo;

    // [스멜5] 강결합 — 협력 객체를 생성자 주입 없이 직접 new 한다. 테스트에서 갈아끼울 수 없다.
    private final SmtpMailSender mail = new SmtpMailSender();
    private final FileAuditLogger audit = new FileAuditLogger();

    public ApprovalService(ApprovalRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    // [스멜8] 파라미터 8개. [스멜9] 이름이 모호하다.
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval d = new Approval();   // d = 결재 문서(Approval 객체)  [스멜9: document? draft? 약어라 의미 불명]
        d.setTitle(title);
        d.setContent(content);
        d.setType(type);
        d.setPriority(urgent ? 3 : priority);   // priority(우선순위): 1 낮음·2 보통·3 높음  [스멜3: 3=높음, 왜 3이 높음? 코드만 봐선 모름]
        d.setStatus(ApprovalStatus.DRAFT);       // [리팩토링] 0 → ApprovalStatus.DRAFT (DB엔 여전히 0 저장)
        d.setDrafterId(drafterId);
        d.setApproverId(approverId);
        d.setAmount(amount);
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        repo.save(d);

        // [스멜4] 감사 로그 기록 — 이 6줄이 submit/approve/reject/cancel 에도 복붙 되어 있다.
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String line = "[" + now + "] APPROVAL CREATE id=" + d.getId()
                + " by=" + drafterId + " type=" + d.getType();
        audit.write(line);
        return d;
    }

    /**
     * 결재 처리 — 상신/승인/반려/취소를 action 코드로 분기한다.
     * [스멜2] 이 메서드 하나가 모든 일을 한다. [스멜1][스멜6] 규칙 계산을 서비스가 떠안는다.
     *
     * action: 1=상신, 2=승인, 3=반려, 9=취소
     */
    public void processApproval(Long id, Long userId, int action, String reason) {
        Approval d = repo.findById(id).orElse(null);   // d = 결재 문서(Approval 객체)
        if (d == null) {
            // [스멜] 예외 대신 조용히 리턴 — 호출자는 실패를 알 수 없다.
            return;
        }
        User u = userRepo.findById(userId).orElse(null);   // u = 사용자(User 객체)
        if (u == null) {
            return;
        }

        ApprovalStatus s = d.getStatus();  // [리팩토링] int → ApprovalStatus. s = status(상태)  [스멜9: 한 글자라 의미 불명]
        int proc = action;          // proc = action(처리 구분): 1 상신·2 승인·3 반려·9 취소  [스멜9: action 을 다른 약어로 또 담음, 의미 없음]

        // [스멜2][스멜3] 거대한 if-지옥. 상태 전이 규칙이 숫자 비교로 흩어져 있다.
        if (proc == 1) {            // proc==1 → 상신 (숫자 1을 외워야 의미를 앎)
            // 상신: 임시저장(0)일 때만 가능
            if (s == ApprovalStatus.DRAFT) {   // 임시저장 상태일 때만
                // [스멜6] 금액 기준 결재자 자동 상향 — 도메인 규칙이 서비스에 박혀 있다.
                if (d.getType() == 1 && d.getAmount() >= 1000000) {   // type 1=지출·2=휴가·3=구매·4=기타 → type==1(지출) && 100만원↑
                    d.setPriority(3);   // 3 = 높음
                }
                d.setStatus(ApprovalStatus.SUBMITTED);
                d.setUpdatedAt(LocalDateTime.now());
                repo.save(d);
                // [스멜4] 메일 발송 — 본문 생성 로직이 곳곳에 복붙.
                User approver = userRepo.findById(d.getApproverId()).orElse(null);
                if (approver != null) {
                    String body = "안녕하세요 " + approver.getName() + "님,\n"
                            + "결재 요청이 도착했습니다.\n제목: " + d.getTitle()
                            + "\n기안자ID: " + d.getDrafterId();
                    mail.send(approver.getEmail(), "[결재요청] " + d.getTitle(), body);
                }
                writeAudit("APPROVAL SUBMIT", d.getId(), userId);
            }
        } else if (proc == 2) {     // proc==2 → 승인
            // 승인: 상신(1) 상태 + 본인이 결재자 + 권한(role>=2) 일 때만
            if (s == ApprovalStatus.SUBMITTED) {   // 상신 상태일 때만 승인 가능
                if (d.getApproverId() != null && d.getApproverId().equals(userId)) {
                    if (u.getRole() >= 2) {   // role 1=사원·2=팀장·3=임원 (role>=2 승인권한)  [스멜3: 숫자로 권한 판정]
                        d.setStatus(ApprovalStatus.APPROVED);
                        d.setUpdatedAt(LocalDateTime.now());
                        repo.save(d);
                        // [스멜4] 또 복붙된 메일 발송
                        User drafter = userRepo.findById(d.getDrafterId()).orElse(null);
                        if (drafter != null) {
                            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                                    + "결재가 승인되었습니다.\n제목: " + d.getTitle();
                            mail.send(drafter.getEmail(), "[결재승인] " + d.getTitle(), body);
                        }
                        writeAudit("APPROVAL APPROVE", d.getId(), userId);
                    }
                }
            }
        } else if (proc == 3) {     // proc==3 → 반려
            // 반려
            if (s == ApprovalStatus.SUBMITTED) {   // 상신 상태만 반려 가능
                if (d.getApproverId() != null && d.getApproverId().equals(userId)) {
                    if (u.getRole() >= 2) {   // role>=2 → 팀장 이상 (위 승인 분기와 똑같은 판정 복붙)
                        d.setStatus(ApprovalStatus.REJECTED);
                        d.setRejectReason(reason);
                        d.setUpdatedAt(LocalDateTime.now());
                        repo.save(d);
                        User drafter = userRepo.findById(d.getDrafterId()).orElse(null);
                        if (drafter != null) {
                            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                                    + "결재가 반려되었습니다.\n제목: " + d.getTitle()
                                    + "\n사유: " + reason;
                            mail.send(drafter.getEmail(), "[결재반려] " + d.getTitle(), body);
                        }
                        writeAudit("APPROVAL REJECT", d.getId(), userId);
                    }
                }
            }
        } else if (proc == 9) {     // proc==9 → 취소 (왜 9? 4~8 은 비워둔 규칙 없는 번호)
            // 취소: 기안자 본인 + 아직 승인 전(0 또는 1)
            if (s == ApprovalStatus.DRAFT || s == ApprovalStatus.SUBMITTED) {  // 임시저장 또는 상신일 때만
                if (d.getDrafterId() != null && d.getDrafterId().equals(userId)) {
                    d.setStatus(ApprovalStatus.CANCELED);
                    d.setUpdatedAt(LocalDateTime.now());
                    repo.save(d);
                    writeAudit("APPROVAL CANCEL", d.getId(), userId);
                }
            }
        }
    }

    // [스멜4] 그나마 추출했지만 create() 안에는 또 복붙이 남아 있다(불완전한 중복 제거).
    private void writeAudit(String act, Long id, Long userId) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        audit.write("[" + now + "] " + act + " id=" + id + " by=" + userId);
    }

    // [스멜1][스멜10] 화면 표시용 문자열까지 서비스가 만든다. 주석으로 매직넘버를 변명한다.
    public String statusLabel(Approval d) {
        ApprovalStatus s = d.getStatus();  // [리팩토링] int → ApprovalStatus
        String tmp;          // tmp = 결과 라벨(반환할 상태 표시 문자열)  [스멜9: 임시? 의도가 안 드러남]
        if (s == ApprovalStatus.DRAFT) tmp = "임시저장";       // 상태를 라벨로 번역 — 이 표가 곳곳에 중복
        else if (s == ApprovalStatus.SUBMITTED) tmp = "상신";
        else if (s == ApprovalStatus.APPROVED) tmp = "승인";
        else if (s == ApprovalStatus.REJECTED) tmp = "반려";
        else if (s == ApprovalStatus.CANCELED) tmp = "취소";
        else tmp = "알수없음";              // enum 전환 후엔 이론상 도달 불가 — 방어적으로 유지(동작 변경 금지)
        return tmp;
    }

    // [스멜6] Feature Envy — Approval 데이터를 꺼내 금액 등급을 서비스가 계산.
    public String amountGrade(Approval d) {
        long a = d.getAmount();   // a = amount(금액, 원)  [스멜9: 한 글자 약어]
        if (a >= 10000000) return "S";   // [스멜3] 1000만원=S — 기준 숫자의 의미가 코드에 없음
        else if (a >= 1000000) return "A";   // 100만원=A
        else if (a >= 100000) return "B";    // 10만원=B
        else return "C";
    }

    public List<Approval> myDrafts(Long userId) {
        return repo.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return repo.findByApproverId(userId);
    }
}
