package com.kkiri.service.serviceImpl;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.kkiri.service.ollamaService;

@Service
public class ollamaServiceImpl implements ollamaService{
	private final RestTemplate restT = new RestTemplate();
	private Process ollamaProcess;
	
	@Override
	public void ensureServerRunning() {
		if(isPortOpen(11434)) {
			return;
		}
		try {
			ProcessBuilder pb = new ProcessBuilder("ollama", "serve");
			
			ollamaProcess = pb.start();
			Thread.sleep(3000);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public String getChatResponse(String message) {
		ensureServerRunning();
		
		String ollamaUrl = "http://localhost:11434/api/generate";
		String systemPrompt = loadSystemPrompt();
		Map<String, Object> requestBody = new HashMap<>();
		
		requestBody.put("model", "gemma3:4b");
		String structuredPrompt = "<|system|>\n" + systemPrompt + "\n<|user|>\n" + message + "\n<|assistant|>\n";
		requestBody.put("prompt", structuredPrompt);
		requestBody.put("Stream", false);
		
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> response = restT.postForObject(ollamaUrl, requestBody, Map.class);
			if(response != null && response.containsKey("response")) {
				return (String) response.get("response");
			}
			return "응답 데이터를 찾을 수 없습니다.";
			
		} catch(Exception e) {
			return "AI 응답생성 실패: " + e.getMessage();
		}
	}
	
	private boolean isPortOpen(int port) {
		try(Socket socket = new Socket("localhost", port)) {
			return true;
		} catch(Exception e) {
			return false;
		}
	}
	
	private String loadSystemPrompt() {
		try {
			ClassPathResource resource = new ClassPathResource("/ai/system-prompt.txt");
			
			Path path = resource.getFile().toPath();
			return Files.readString(path, StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.err.println("프롬프트 파일 로드 실패: " + e.getMessage());
			return "당신은 친절한 고객센터 상담원입니다.";
		}
	}
}


