#!/usr/bin/env python
# coding: utf-8

"""
[Analysis Script v0.3.0]
- 핵심 변경: 그룹 식별자(Group ID) 기반 동적 분석 파이프라인 완성
- 기술 세부사항:
  1. Java ProcessBuilder로부터 전달된 sys.argv[1] (groupId) 수신 로직 구현
  2. Oracle SQL Bind Variables(:1)를 활용한 그룹별 데이터 격리(Data Isolation) 조회
  3. 비정상적 접근 또는 데이터 부재 시(df.empty) 방어적 JSON 결과 생성 로직 강화
- 작성일: 2026-03-18
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

def get_db_data(group_id): # 매개변수 추가
    db_config = {
        "user": "kkiri2",
        "password": "1234",
        "dsn": "192.168.51.7:1521/xe"
    }
    try:
        conn = oracledb.connect(user=db_config["user"], password=db_config["password"], dsn=db_config["dsn"])
        # 특정 그룹의 지출 내역만 조회
        query = """
            SELECT 
                TO_CHAR(CREATED_AT, 'YYYY-MM-DD') as "date", 
                MERCHANT_NAME as "title", 
                CATEGORY as "category", 
                AMOUNT as "amount"
            FROM EXPENSES
            WHERE GROUP_ID = :1
            ORDER BY CREATED_AT DESC
        """
        df = pd.read_sql(query, conn, params=[group_id]) # 필터 적용
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

# --- 실행 시작 부분 ---

# 1. 인자 수신 (인자 없으면 빈 문자열)
target_group_id = sys.argv[1] if len(sys.argv) > 1 else ""

# 2. 데이터 조회 로직 통합
if not target_group_id:
    print("❌ [Python] 전달된 그룹 ID가 없습니다.")
    df = pd.DataFrame() 
else:
    print(f"🔍 [Python] 분석 대상 그룹 ID: {target_group_id}")
    df = get_db_data(target_group_id)

# ⚠️ 기존에 중복으로 있던 'df = get_db_data(target_group_id)' 줄은 반드시 지워주세요!

if df.empty:
    print(f"[Empty] {target_group_id}번 그룹의 데이터가 없습니다.")
    final_result = {
        "recentHeadline": "내역 없음", 
        "recentCommentary": "해당 그룹의 영수증을 등록해주세요.",
        "recentPercent": 0,
        "totalAmount": 0,
        "labels": [],
        "dataValues": [],
        "aiHeadline": "데이터 부족",
        "aiCommentary": "분석할 소비 데이터가 없습니다.",
        "foodAmount": 0,
        "foodPercent": 0,
        "anomalies": []
    }
else:
    # --- (이후 분석 로직은 동일) ---
    recent_item = df.iloc[0]
    avg_amount = df['amount'].mean() if len(df) > 0 else 0
    recent_val = float(recent_item['amount'])
    
    recent_percent = int((recent_val / avg_amount) * 100) if avg_amount > 0 else 100
    if recent_percent > 200: recent_percent = 200 

    recent_prompt = f"방금 등록된 지출: {recent_item['title']} / 금액: {recent_item['amount']}원 / 카테고리: {recent_item['category']}"
    recent_ai_raw = ask_ai(
        "너는 모임 회계 전문가야. 방금 들어온 이 지출 1건이 모임 성격에 적절한지, 정산 시 주의할 점은 없는지 2문장으로 짧게 조언해줘. 헤드라인: [내용], 코멘트: [내용] 형식을 지켜.",
        recent_prompt
    )

    total_amount = int(df['amount'].sum())
    category_sum = df.groupby('category')['amount'].sum()
    summary_text = "\n".join([f"- {cat}: {int(amt)}원" for cat, amt in category_sum.items()])
    
    accumulated_prompt = f"모임 총 지출: {total_amount}원\n상세 비중:\n{summary_text}"
    accumulated_ai_raw = ask_ai(
        "너는 모임 운영 전문가야. 모임의 전체 소비 패턴을 분석해서 향후 회비 운영이나 절약 방안을 2문장으로 조언해줘. 헤드라인: [내용], 코멘트: [내용] 형식을 지켜.",
        accumulated_prompt
    )

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

    final_result = {
        "recentHeadline": recent_h,
        "recentCommentary": recent_c,
        "recentPercent": recent_percent,
        "totalAmount": total_amount,
        "labels": list(category_sum.index),
        "dataValues": [int((amt/total_amount)*100) for amt in category_sum.values],
        "aiHeadline": accum_h,
        "aiCommentary": accum_c,
        "foodAmount": int(category_sum.get('식비', 0)),
        "foodPercent": int((category_sum.get('식비', 0) / total_amount) * 100) if total_amount > 0 else 0,
        "anomalies": []
    }

with open('result.json', 'w', encoding='utf-8') as f:
    json.dump(final_result, f, ensure_ascii=False, indent=4)

print("[SUCCESS] Dual analysis completed.")