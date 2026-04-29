package com.kkiri.service;

import com.kkiri.model.dto.AnalysisResponse; // 분석 결과 데이터를 담는 DTO 임포트

/**
 * AI 분석 관련 비즈니스 로직을 정의하는 인터페이스
 * 컨트롤러는 이 인터페이스를 의존성 주입(DI)받아 사용하여 결합도를 낮춤
 */
public interface AnalysisService {
    /**
     * AI 분석을 수행하고 그 결과를 반환하는 추상 메서드
     * @param groupId 분석 대상이 되는 그룹의 식별자
     * @return AI 분석 결과가 담긴 AnalysisResponse 객체
     */
    // Long 타입의 groupId를 인자로 받도록 수정
    AnalysisResponse getAiAnalysis(Long groupId); 
}