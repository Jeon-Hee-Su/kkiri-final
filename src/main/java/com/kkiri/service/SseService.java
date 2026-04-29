package com.kkiri.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    // 이메일 → SseEmitter 매핑 (동시성 안전한 Map)
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 클라이언트 SSE 연결 등록
    public SseEmitter register(String email) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30분 유지

        emitter.onCompletion(() -> emitters.remove(email));
        emitter.onTimeout(() -> emitters.remove(email));
        emitter.onError(e -> emitters.remove(email));

        emitters.put(email, emitter);
        log.info("SSE 연결 등록: {}", email);

        // 연결 즉시 확인용 이벤트 전송 (브라우저 연결 유지용)
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(email);
        }

        return emitter;
    }

    // 강제 로그아웃 이벤트 푸시
    public void sendForceLogout(String email) {
        SseEmitter emitter = emitters.get(email);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("force-logout")
                        .data("다른 기기에서 로그인되어 자동 로그아웃됩니다."));
                log.info("강제 로그아웃 이벤트 전송: {}", email);
            } catch (IOException e) {
                emitters.remove(email);
                log.warn("SSE 전송 실패 (연결 끊김): {}", email);
            }
        }
    }

    // QR 결제 완료 이벤트 푸시 (결제자 화면의 QR 모달 갱신용)
    public void sendQrPaymentComplete(String email, long deductedAmount, long remainingBalance) {
        SseEmitter emitter = emitters.get(email);
        if (emitter != null) {
            try {
                String data = deductedAmount + "," + remainingBalance;
                emitter.send(SseEmitter.event()
                        .name("qr-payment-complete")
                        .data(data));
                log.info("QR 결제 완료 이벤트 전송: {} (차감: {}, 잔액: {})", email, deductedAmount, remainingBalance);
            } catch (IOException e) {
                emitters.remove(email);
                log.warn("SSE 전송 실패 (연결 끊김): {}", email);
            }
        }
    }

    // 연결 해제 (로그아웃 시)
    public void disconnect(String email) {
        SseEmitter emitter = emitters.remove(email);
        if (emitter != null) emitter.complete();
    }
}