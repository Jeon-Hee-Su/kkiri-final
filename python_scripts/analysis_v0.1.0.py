#!/usr/bin/env python
# coding: utf-8

# In[1]:


"""
Project: KKIRI AI Household Ledger Analysis
File: analysis_v0.1.0.py
Version: 0.1.0
Description: Oracle DB 연동 및 Ollama(Gemma3)를 이용한 지출 패턴 분석 및 JSON 결과 생성
Author: [JaPa-get-it]
Last Modified: 2026-03-11
"""

import oracledb
import pandas as pd
import json
import ollama

# 1. DB 접속 및 데이터 로드 함수
def get_db_data():
    db_config = {
        "user": "kkiri",
        "password": "1234",
        "dsn": "192.168.51.5:1521/xe"
    }
    try:
        conn = oracledb.connect(user=db_config["user"], password=db_config["password"], dsn=db_config["dsn"])
        # 날짜 형식을 'YYYY-MM-DD'로 명확히 가져옵니다.
        query = """
            SELECT 
                TO_CHAR(CREATED_AT, 'YYYY-MM-DD') as "date", 
                MERCHANT_NAME as "title", 
                CATEGORY as "category", 
                AMOUNT as "amount"
            FROM EXPENSES
            WHERE IS_DELETED = 'N'
            ORDER BY CREATED_AT DESC
        """
        df = pd.read_sql(query, conn)
        conn.close()
        return df
    except Exception as e:
        print(f"❌ DB 접속 실패: {e}")
        return pd.DataFrame()

# --- 실행 시작 ---
df = get_db_data()

if df.empty:
    print("⚠️ 분석할 데이터가 없습니다. 프로그램을 종료합니다.")
else:
    print(f"[SUCCESS] {len(df)} data loaded.")

    # 2. 통계 분석
    total_amount = int(df['amount'].sum())
    category_sum = df.groupby('category')['amount'].sum()

    # 3. 이상 지출 감지 (10만원 이상)
    anomalies_list = []
    anomalies_df = df[df['amount'] >= 100000]
    for _, row in anomalies_df.iterrows():
        # 날짜 포맷팅 (03월 02일)
        date_parts = row['date'].split('-')
        formatted_date = f"{date_parts[1]}월 {date_parts[2]}일"
        anomalies_list.append({
            'date': formatted_date,
            'title': row['title'],
            'amount': int(row['amount']),
            'reason': "단일 지출 금액이 매우 높음 (주의)"
        })

    # 4. AI(Ollama) 리포트 생성
    summary_text = ""
    for cat, amt in category_sum.items():
        percent = (amt / total_amount) * 100
        summary_text += f"- {cat}: {int(percent)}% ({int(amt)}원)\n"

    prompt_data = f"""
    이번 달 총 지출: {total_amount}원
    상세 비중:
    {summary_text}
    가장 큰 지출: '{df.loc[df['amount'].idxmax(), 'title']}' ({int(df['amount'].max())}원)
    """

    print("[INFO] AI Analyzing (Ollama)...")
    response = ollama.chat(model='gemma3:4b', messages=[
        {'role': 'system', 'content': '가계부 전문가로서 헤드라인: [내용], 코멘트: [내용] 형식으로 딱 3문장만 조언해줘.'},
        {'role': 'user', 'content': f"데이터 요약:\n{prompt_data}"}
    ])
    ai_report = response['message']['content']

    # 5. AI 답변 파싱 (헤드라인/코멘트 분리)
    final_headline = "이번 달 소비 분석 결과"
    final_commentary = ai_report.strip()

    if "헤드라인:" in ai_report and "코멘트:" in ai_report:
        try:
            parts = ai_report.split("코멘트:", 1)
            final_headline = parts[0].replace("헤드라인:", "").replace("*", "").strip()
            final_commentary = parts[1].replace("*", "").strip()
        except: pass

    # 6. 최종 JSON 데이터 구성
    final_result = {
        "totalAmount": total_amount,
        "labels": list(category_sum.index),
        "dataValues": [int((amt/total_amount)*100) for amt in category_sum.values],
        "anomalies": anomalies_list,
        "aiHeadline": final_headline,
        "aiCommentary": final_commentary,
        "foodAmount": int(category_sum.get('식비', 0)),
        "foodPercent": int((category_sum.get('식비', 0) / total_amount) * 100) if total_amount > 0 else 0
    }

    # 7. result.json 파일 저장
    with open('result.json', 'w', encoding='utf-8') as f:
        json.dump(final_result, f, ensure_ascii=False, indent=4)

    print("[SUCCESS] Analysis completed! result.json updated.")

