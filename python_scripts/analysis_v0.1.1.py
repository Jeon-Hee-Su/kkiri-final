#!/usr/bin/env python
# coding: utf-8

"""
Project: KKIRI AI Household Ledger Analysis
File: analysis_v0.1.1.py
Version: 0.1.1
Description: Oracle DB 연동 및 Ollama 분석 결과 생성 (코드 가독성 및 유지보수를 위한 상세 주석 추가)
Author: [JaPa-get-it]
Last Modified: 2026-03-12
"""

import oracledb   # Oracle DB 연결을 위한 라이브러리 임포트
import pandas as pd # 데이터 분석 및 조작을 위한 Pandas 라이브러리 임포트
import json       # 분석 결과를 JSON 형식으로 저장하기 위한 라이브러리 임포트
import ollama     # 로컬 AI 모델(Gemma3) 통신을 위한 라이브러리 임포트

# 1. DB 접속 및 데이터 로드 함수
def get_db_data():
    # 데이터베이스 접속 정보 설정 (계정, 비밀번호, 주소)
    db_config = {
        "user": "kkiri",
        "password": "1234",
        "dsn": "192.168.51.5:1521/xe"
    }
    try:
        # 설정된 정보를 바탕으로 Oracle DB 접속 시도
        conn = oracledb.connect(user=db_config["user"], password=db_config["password"], dsn=db_config["dsn"])
        # 지출 데이터를 가져오기 위한 SQL 쿼리 (날짜 형식 지정 및 최신순 정렬)
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
        # Pandas의 read_sql을 사용하여 쿼리 결과를 데이터프레임 구조로 로드
        df = pd.read_sql(query, conn)
        conn.close() # 데이터 로드 완료 후 DB 연결 종료
        return df # 로드된 데이터프레임 반환
    except Exception as e:
        # DB 접속 실패 시 에러 메시지 출력
        print(f"❌ DB 접속 실패: {e}")
        return pd.DataFrame() # 빈 데이터프레임 반환하여 프로그램 중단 방지

# --- 실행 시작 ---
# 정의한 함수를 호출하여 DB로부터 데이터를 가져옴
df = get_db_data()

# 데이터가 비어있는지 확인
if df.empty:
    print("⚠️ 분석할 데이터가 없습니다. 프로그램을 종료합니다.")
else:
    # 성공적으로 데이터를 불러왔을 경우 건수 출력
    print(f"[SUCCESS] {len(df)} data loaded.")

    # 2. 통계 분석
    # 지출 금액(amount) 컬럼의 모든 합계를 구함
    total_amount = int(df['amount'].sum())
    # 카테고리별로 그룹화하여 각각의 지출 합계를 계산
    category_sum = df.groupby('category')['amount'].sum()

    # 3. 이상 지출 감지 (10만원 이상 고액 결제 추출)
    anomalies_list = []
    # 금액이 100,000원 이상인 데이터만 필터링
    anomalies_df = df[df['amount'] >= 100000]
    # 필터링된 데이터를 하나씩 순회하며 리스트에 추가
    for _, row in anomalies_df.iterrows():
        # 날짜 형식 변환 (예: 2026-03-02 -> 03월 02일)
        date_parts = row['date'].split('-')
        formatted_date = f"{date_parts[1]}월 {date_parts[2]}일"
        # Java DTO 구조에 맞춰 이상 지출 객체 생성
        anomalies_list.append({
            'date': formatted_date,
            'title': row['title'],
            'amount': int(row['amount']),
            'reason': "단일 지출 금액이 매우 높음 (주의)"
        })

    # 4. AI(Ollama) 리포트 생성을 위한 프롬프트 가공
    summary_text = ""
    # 카테고리별 비중(%)과 금액을 텍스트로 정리
    for cat, amt in category_sum.items():
        percent = (amt / total_amount) * 100
        summary_text += f"- {cat}: {int(percent)}% ({int(amt)}원)\n"

    # AI에게 전달할 최종 데이터 요약 정보 구성
    prompt_data = f"""
    이번 달 총 지출: {total_amount}원
    상세 비중:
    {summary_text}
    가장 큰 지출: '{df.loc[df['amount'].idxmax(), 'title']}' ({int(df['amount'].max())}원)
    """

    print("[INFO] AI Analyzing (Ollama)...")
    # Ollama 라이브러리를 통해 로컬 AI 모델(gemma3) 호출 및 분석 요청
    response = ollama.chat(model='gemma3:4b', messages=[
        {'role': 'system', 'content': '가계부 전문가로서 헤드라인: [내용], 코멘트: [내용] 형식으로 딱 3문장만 조언해줘.'},
        {'role': 'user', 'content': f"데이터 요약:\n{prompt_data}"}
    ])
    # AI로부터 받은 답변 내용 저장
    ai_report = response['message']['content']

    # 5. AI 답변 파싱 (헤드라인과 코멘트를 구분하여 추출)
    # 기본값 설정 (파싱 실패 대비)
    final_headline = "이번 달 소비 분석 결과"
    final_commentary = ai_report.strip()

    # AI 답변 내에 '헤드라인:'과 '코멘트:' 키워드가 모두 있는지 확인
    if "헤드라인:" in ai_report and "코멘트:" in ai_report:
        try:
            # '코멘트:'를 기준으로 앞부분(헤드라인)과 뒷부분(코멘트) 분리
            parts = ai_report.split("코멘트:", 1)
            # 불필요한 공백 및 마크다운 기호(*) 제거
            final_headline = parts[0].replace("헤드라인:", "").replace("*", "").strip()
            final_commentary = parts[1].replace("*", "").strip()
        except: pass # 파싱 에러 시 기본값 사용

    # 6. 최종 JSON 데이터 구성 (Java의 AnalysisResponse.java 필드명과 일치시켜야 함)
    final_result = {
        "totalAmount": total_amount, # 총액
        "labels": list(category_sum.index), # 차트 범례 이름 리스트
        "dataValues": [int((amt/total_amount)*100) for amt in category_sum.values], # 차트 퍼센트 값 리스트
        "anomalies": anomalies_list, # 이상 지출 목록
        "aiHeadline": final_headline, # AI 헤드라인
        "aiCommentary": final_commentary, # AI 코멘트
        "foodAmount": int(category_sum.get('식비', 0)), # 식비 합계
        "foodPercent": int((category_sum.get('식비', 0) / total_amount) * 100) if total_amount > 0 else 0 # 식비 비중
    }

    # 7. result.json 파일 저장 (Java 서비스가 이 파일을 읽어 화면에 출력함)
    with open('result.json', 'w', encoding='utf-8') as f:
        # 한글 깨짐 방지 처리 및 들여쓰기 적용
        json.dump(final_result, f, ensure_ascii=False, indent=4)

    # 분석 완료 메시지 출력
    print("[SUCCESS] Analysis completed! result.json updated.")

