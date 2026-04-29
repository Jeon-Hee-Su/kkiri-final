#!/usr/bin/env python
# coding: utf-8

"""
Project: KKIRI AI Group Account Analysis
File: analysis_v0.2.0.py
Version: 0.2.0 (Enhanced Dual Analysis: Gauge & Trend)
Description: 
    1. 최근 영수증 1건에 대한 실시간 적절성 분석 및 그룹 평균 대비 지출 강도(%) 계산
    2. 전체 누적 지출 데이터 기반 카테고리별 비중 및 운영 조언 생성
    3. Java DTO(AnalysisResponse)와 호환되는 JSON 리포트 출력
"""

import oracledb
import pandas as pd
import json
import ollama
import sys
import io

# 윈도우 콘솔 한글 깨짐 방지
sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.detach(), encoding='utf-8')

def get_db_data():
    db_config = {
        "user": "kkiri2",
        "password": "1234",
        "dsn": "192.168.51.5:1521/xe"
    }
    try:
        conn = oracledb.connect(user=db_config["user"], password=db_config["password"], dsn=db_config["dsn"])
        # 전체 지출 내역 조회
        query = """
            SELECT 
                TO_CHAR(CREATED_AT, 'YYYY-MM-DD') as "date", 
                MERCHANT_NAME as "title", 
                CATEGORY as "category", 
                AMOUNT as "amount"
            FROM EXPENSES
            ORDER BY CREATED_AT DESC
        """
        df = pd.read_sql(query, conn)
        conn.close()
        return df
    except Exception as e:
        print(f"[DB Error] {str(e)}")
        return pd.DataFrame()

def ask_ai(system_role, user_content):
    try:
        response = ollama.chat(model='gemma3:4b', messages=[
            {'role': 'system', 'content': system_role},
            {'role': 'user', 'content': user_content}
        ])
        return response['message']['content']
    except Exception:
        return "헤드라인: 분석 불가 / 코멘트: AI 연결을 확인해주세요."

# --- 실행 시작 ---
df = get_db_data()

if df.empty:
    print("[Empty] 분석할 데이터가 없습니다.")
    final_result = {
        "recent": {"headline": "내역 없음", "commentary": "영수증을 등록해주세요."},
        "accumulated": {"totalAmount": 0, "headline": "데이터 없음", "commentary": "지출 내역이 없습니다."}
    }
else:
    # 1. 최근 영수증 1건 분석 (Recent Analysis)
    recent_item = df.iloc[0]

    # --- [추가 시작] 평균 대비 비율 계산 로직 ---
    # 전체 지출액의 평균을 구합니다.
    avg_amount = df['amount'].mean() if len(df) > 0 else 0
    recent_val = float(recent_item['amount'])
    
    # 평균 대비 비율 계산 (예: 100%면 평균만큼 쓴 것, 150%면 평균보다 1.5배 더 쓴 것)
    # 게이지 차트의 시각적 안정성을 위해 최대값을 200%로 제한합니다.
    recent_percent = int((recent_val / avg_amount) * 100) if avg_amount > 0 else 100
    if recent_percent > 200: recent_percent = 200 

    recent_prompt = f"방금 등록된 지출: {recent_item['title']} / 금액: {recent_item['amount']}원 / 카테고리: {recent_item['category']}"
    recent_ai_raw = ask_ai(
        "너는 모임 회계 전문가야. 방금 들어온 이 지출 1건이 모임 성격에 적절한지, 정산 시 주의할 점은 없는지 2문장으로 짧게 조언해줘. 헤드라인: [내용], 코멘트: [내용] 형식을 지켜.",
        recent_prompt
    )

    # 2. 전체 누적 데이터 분석 (Accumulated Analysis)
    total_amount = int(df['amount'].sum())
    category_sum = df.groupby('category')['amount'].sum()
    summary_text = "\n".join([f"- {cat}: {int(amt)}원" for cat, amt in category_sum.items()])
    
    accumulated_prompt = f"모임 총 지출: {total_amount}원\n상세 비중:\n{summary_text}"
    accumulated_ai_raw = ask_ai(
        "너는 모임 운영 전문가야. 모임의 전체 소비 패턴을 분석해서 향후 회비 운영이나 절약 방안을 2문장으로 조언해줘. 헤드라인: [내용], 코멘트: [내용] 형식을 지켜.",
        accumulated_prompt
    )

    # 파싱 함수 (헤드라인/코멘트 분리)
    def parse_report(raw_text):
        h, c = "분석 결과", raw_text
        if "헤드라인:" in raw_text and "코멘트:" in raw_text:
            try:
                parts = raw_text.split("코멘트:", 1)
                h = parts[0].replace("헤드라인:", "").replace("*", "").strip()
                c = parts[1].replace("*", "").strip()
            except: pass
        return h, c

    recent_h, recent_c = parse_report(recent_ai_raw)
    accum_h, accum_c = parse_report(accumulated_ai_raw)

    # 3. 최종 JSON 구조화 (기존 AnalysisResponse DTO 필드와 호환성 유지)
    final_result = {
        # --- (신규) 최근 지출 리포트 ---
        "recentHeadline": recent_h,
        "recentCommentary": recent_c,
        "recentPercent": recent_percent,
        
        # --- (기존) 누적 분석 리포트 ---
        "totalAmount": total_amount,
        "labels": list(category_sum.index),
        "dataValues": [int((amt/total_amount)*100) for amt in category_sum.values],
        "aiHeadline": accum_h,
        "aiCommentary": accum_c,
        "foodAmount": int(category_sum.get('식비', 0)),
        "foodPercent": int((category_sum.get('식비', 0) / total_amount) * 100) if total_amount > 0 else 0,
        "anomalies": [] # 필요 시 기존 로직 추가 가능
    }

# 4. result.json 저장
with open('result.json', 'w', encoding='utf-8') as f:
    json.dump(final_result, f, ensure_ascii=False, indent=4)

print("[SUCCESS] Dual analysis completed.")