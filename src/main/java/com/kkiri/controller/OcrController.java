package com.kkiri.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kkiri.service.OcrService;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    @Autowired
    private OcrService ocrService;

    @GetMapping("/analyze")
    public ResponseEntity<String> analyze(@RequestParam String path) {
        // 예: http://localhost:8080/api/ocr/analyze?path=C:/Users/tony/receipt.jpg
        String jsonResult = ocrService.executeOcr(path);
        return ResponseEntity.ok(jsonResult);
    }
}