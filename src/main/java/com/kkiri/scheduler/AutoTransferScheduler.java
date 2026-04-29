package com.kkiri.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.kkiri.service.AutoTransferService;

import lombok.RequiredArgsConstructor;

/**
 * 자동이체 스케줄러
 *
 * - 매일 오전 0시에 실행 (cron 변경으로 조정 가능)
 * - DemoApplication에 @EnableScheduling 이 이미 선언되어 있으므로 별도 설정 불필요
 *
 * ※ cron 표현식: "초 분 시 일 월 요일"
 *    "0 0 9 * * *"  → 매일 09:00:00
 */
@Component
@RequiredArgsConstructor
public class AutoTransferScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoTransferScheduler.class);

    private final AutoTransferService autoTransferService;

    /**
     * 매일 오전 00시 자동이체 실행
     *
     * 테스트 시에는 fixedDelay 방식으로 교체해 빠르게 확인할 수 있습니다:
     *   @Scheduled(fixedDelay = 60_000)  // 1분마다
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void runAutoTransfer() {
        log.info("===== [자동이체 스케줄러] 실행 시작 =====");
        try {
            autoTransferService.executeAutoTransfers();
        } catch (Exception e) {
            log.error("[자동이체 스케줄러] 실행 중 예외 발생", e);
        }
        log.info("===== [자동이체 스케줄러] 실행 종료 =====");
    }
}