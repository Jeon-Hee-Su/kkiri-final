package com.kkiri.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkiri.service.AutoTransferService;

import lombok.RequiredArgsConstructor;

/**
 * 자동이체 수동 트리거 컨트롤러 (관리자/테스트용)
 *
 * POST /api/admin/auto-transfer/run
 * - 스케줄러 없이 즉시 자동이체를 실행하고 싶을 때 사용
 * - 운영 환경에서는 SecurityConfig에서 ADMIN 역할만 허용하도록 설정 필요
 */
@RestController
@RequestMapping("/api/admin/auto-transfer")
@RequiredArgsConstructor
public class AutoTransferController {

    private static final Logger log = LoggerFactory.getLogger(AutoTransferController.class);

    private final AutoTransferService autoTransferService;

    /**
     * 자동이체 즉시 실행 (테스트/수동 트리거)
     */
    @PostMapping("/run")
    public ResponseEntity<String> runNow() {
        log.info("[자동이체] 수동 트리거 요청");
        autoTransferService.executeAutoTransfers();
        return ResponseEntity.ok("자동이체 실행 완료");
    }
}