package com.kkiri.service.serviceImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkiri.mapper.AutoTransferMapper;
import com.kkiri.service.AutoTransferService;
import com.kkiri.service.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * 자동이체 서비스 구현체
 *
 * 처리 흐름:
 *  1. 오늘이 납부일(DUE_DAY)인 그룹의 자동이체 대상 멤버를 조회
 *  2. 각 멤버별로 트랜잭션 내에서:
 *     a. 개인계좌 잔액 차감  (잔액 부족 시 → FAILED 기록 후 알림)
 *     b. 그룹계좌 잔액 증가
 *     c. 거래내역(TRANSACTIONS) 기록
 *     d. 알림 발송 (성공/실패)
 */
@Service
@RequiredArgsConstructor
public class AutoTransferServiceImpl implements AutoTransferService {

    private static final Logger log = LoggerFactory.getLogger(AutoTransferServiceImpl.class);

    private final AutoTransferMapper autoTransferMapper;
    private final NotificationService notificationService;

    @Override
    public void executeAutoTransfers() {
        int today = LocalDate.now().getDayOfMonth();
        log.info("[자동이체] 스케줄러 실행 - 오늘 날짜: {}일", today);

        List<Map<String, Object>> targets = autoTransferMapper.findAutoTransferTargets(today);
        log.info("[자동이체] 대상 건수: {}건", targets.size());

        for (Map<String, Object> target : targets) {
            processSingleTransfer(target);
        }

        log.info("[자동이체] 전체 처리 완료");
    }

    /**
     * 단건 자동이체 처리
     * - @Transactional(noRollbackFor = Exception.class) 로 선언해
     *   한 멤버 실패가 다른 멤버 롤백에 영향 주지 않도록 격리
     */
    @Transactional
    public void processSingleTransfer(Map<String, Object> target) {
        int    userId    = toInt(target.get("userId"));
        int    groupId   = toInt(target.get("groupId"));
        int    accountId = toInt(target.get("accountId"));
        long   amount    = toLong(target.get("subscriptionFee"));
        String groupName = String.valueOf(target.get("groupName"));

        String merchantUid = "AUTO_" + groupId + "_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8);

        try {
            // 1. 개인계좌 잔액 차감 (잔액 부족 시 UPDATE 영향 행 = 0)
            int updated = autoTransferMapper.deductUserAccountBalance(accountId, amount);

            if (updated == 0) {
                // 잔액 부족 → FAILED 기록 + 알림
                log.warn("[자동이체] 잔액 부족 - userId={}, groupId={}, amount={}", userId, groupId, amount);

                autoTransferMapper.insertAutoTransferTransaction(
                        groupId, userId, accountId, amount,
                        merchantUid, "FAILED",
                        "[" + groupName + "] 자동이체 실패 - 잔액 부족"
                );

                notificationService.createNotification(
                        userId,
                        "💸 [" + groupName + "] 자동이체 실패\n잔액이 부족합니다. 계좌를 충전 후 직접 납부해 주세요.",
                        "GROUP",
                        groupId
                );
                return;
            }

            // 2. 그룹계좌 잔액 증가
            autoTransferMapper.increaseGroupBalance(groupId, amount);

            // 3. 거래내역 기록 (SUCCESS)
            autoTransferMapper.insertAutoTransferTransaction(
                    groupId, userId, accountId, amount,
                    merchantUid, "SUCCESS",
                    "[" + groupName + "] 월 회비 자동이체"
            );

            // 4. 성공 알림
            notificationService.createNotification(
                    userId,
                    "✅ [" + groupName + "] 회비 " + String.format("%,d", amount) + "원이 자동이체되었습니다.",
                    "GROUP",
                    groupId
            );

            log.info("[자동이체] 성공 - userId={}, groupId={}, amount={}", userId, groupId, amount);

        } catch (Exception e) {
            // 예상치 못한 예외: FAILED 기록 후 계속 진행
            log.error("[자동이체] 처리 중 예외 - userId={}, groupId={}, error={}", userId, groupId, e.getMessage(), e);

            try {
                autoTransferMapper.insertAutoTransferTransaction(
                        groupId, userId, accountId, amount,
                        merchantUid, "FAILED",
                        "[" + groupName + "] 자동이체 오류: " + e.getMessage()
                );
            } catch (Exception insertEx) {
                log.error("[자동이체] 실패 내역 기록 중 2차 예외 발생", insertEx);
            }
        }
    }

    // ── 타입 변환 헬퍼 ──────────────────────────────────────────
    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }
}