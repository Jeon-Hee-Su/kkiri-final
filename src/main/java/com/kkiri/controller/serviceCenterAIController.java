package com.kkiri.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkiri.service.ollamaService;

@RestController
@RequestMapping("/api/aiService")
public class serviceCenterAIController {
	
    private final ollamaService olservice;
    // 인터페이스를 주입받아 사용합니다. 실제 로직은 구현체인 OllamaServiceImpl에서 돌아갑니다.

    public serviceCenterAIController(ollamaService olservice) {
        this.olservice = olservice;	
    }
    // 생성자 주입을 통해 스프링 빈으로 등록된 서비스를 가져옵니다.

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> payload) {
        String userMsg = payload.get("message");
        // JS에서 보낸 JSON 데이터 중 "message" 키에 해당하는 값을 추출합니다.
        
        try {
            String aiReply = olservice.getChatResponse(userMsg);
            // 서비스 계층에 질문을 던지고 답변을 받아옵니다. 이 내부에서 Ollama 실행 여부도 체크합니다.
            
            Map<String, String> result = new HashMap<>();
            result.put("reply", aiReply);
            // 결과를 다시 JSON 형태인 { "reply": "답변내용" }으로 변환하여 반환합니다.
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> errorResult = new HashMap<>();
            errorResult.put("reply", "에러 발생: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResult);
            // 예외 발생 시 500 에러와 함께 원인 메시지를 클라이언트에 보냅니다.
        }
    }
}