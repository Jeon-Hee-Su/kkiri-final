package com.kkiri.model.dto;

import lombok.Data;
import java.util.List;

/**
 * AI 소비 분석 결과를 담는 데이터 전송 객체 (DTO)
 * Python 분석 스크립트가 생성한 result.json 파일과 필드명이 매칭되어야 함
 */
@Data
public class AnalysisResponse {
	private String recentHeadline;
    private String recentCommentary;
    private int recentPercent;
    private int totalAmount; // 이번 달 그룹의 전체 지출 합계 (메인 요약 숫자용)
    private List<String> labels; // 차트의 X축 또는 범례 항목 (예: "회식", "비품", "교통")
    private List<Integer> dataValues; // 차트의 각 항목별 수치 (예: 150000, 20000, 5000)
    private List<Anomaly> anomalies; // AI가 감지한 이상 지출(평소와 다른 고액 등) 리스트
    private String aiHeadline; // AI가 생성한 분석 요약 헤드라인 (상단 문구용)
    private String aiCommentary; // AI가 생성한 상세 분석 및 조언 (본문용)
    private int foodAmount; // 모임통장에서 가장 큰 비중인 '식비' 카테고리의 절대 금액
    private int foodPercent; // 전체 대비 식비가 차지하는 백분율 (차트 중앙 텍스트용)
    
    /**
     * 이상 지출 상세 정보를 담는 내부 정적 클래스
     */
    @Data
    public static class Anomaly {
        private String date;	// 지출 발생 일자
        private String title;	// 지출처(상호명)
        private int amount;		// 지출 금액
        private String reason;	// AI가 분석한 이상 지출 판단 사유 (예: "평균 대비 5배 지출")
    }
}