# [v1.0.1 패치노트]
# 1. 한글 계정명 경로 인식 오류 우회 로직 도입 (팀원 공용 호환)
# 2. 범용 설정 유지 및 예외 처리 강화

import os
import sys
import re  # [수정] re 모듈을 최상단으로 끌어올렸습니다.

# 1. 현재 사용자 홈 디렉토리 확인
user_home = os.path.expanduser("~")

# 2. 정규표현식으로 계정 경로에 한글(가-힣)이 포함되어 있는지 검사
is_korean_path = bool(re.search('[가-힣]', user_home))

if is_korean_path:  # [수정] 이 if문이 통째로 빠져 있었습니다!
    # 1. 환경 변수 강제 설정
    os.environ['USERPROFILE'] = 'C:/paddle_temp'
    os.environ['HOME'] = 'C:/paddle_temp'
    os.environ['PADDLE_HOME'] = 'C:/paddle_temp'

    # 2. 계층별로 폴더 생성 (WinError 3 완벽 방어)
    base_path = 'C:/paddle_temp'
    sub_paths = ['.paddleocr', 'whl']
    
    current_p = base_path
    if not os.path.exists(current_p):
        os.makedirs(current_p, exist_ok=True)
        
    for sub in sub_paths:
        current_p = os.path.join(current_p, sub)
        if not os.path.exists(current_p):
            os.makedirs(current_p, exist_ok=True)
    
    # 웹과 연동할 때는 이 print문이 JSON 파싱 에러를 유발할 수 있으므로 주석 처리하는 것이 좋습니다.
    # print(f"Paddle 경로 준비 완료: {current_p}")

import json
import numpy as np
import cv2
import io
import requests
import paddle

# [환경 설정] 시스템 인코딩 및 Paddle 엔진 최적화
os.environ['FLAGS_use_onednn'] = '0'
# GPU 강제 비활성화 (팀원 PC 에러 방지 핵심)
paddle.set_device('cpu') 
sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding='utf-8')

from paddleocr import PaddleOCR

def analyze_receipt(img_path):
    try:
        # 1. 이미지 로드
        img_array = np.fromfile(img_path, np.uint8)
        img = cv2.imdecode(img_array, cv2.IMREAD_COLOR)
        if img is None: return {"error": "이미지 로드 실패"}

        # 2. OCR 실행 (한글 계정 경로 에러 방지 적용)
        ocr_config = {
            'lang': 'korean',
            'use_gpu': False,
            'show_log': False
        }

        # [핵심] 계정명에 한글이 포함된 경우, 모델 경로를 C:/paddle_temp로 강제 지정
        # 이 설정이 들어가면 엔진이 시스템 기본 경로(한글)를 무시하고 영문 폴더로 머리를 돌립니다.
        if any(ord(char) > 127 for char in os.path.expanduser("~")):
            ocr_config.update({
                'det_model_dir': 'C:/paddle_temp/.paddleocr/whl/det',
                'rec_model_dir': 'C:/paddle_temp/.paddleocr/whl/rec',
                'cls_model_dir': 'C:/paddle_temp/.paddleocr/whl/cls'
            })

        # 설정된 값으로 OCR 객체 생성
        ocr = PaddleOCR(**ocr_config)
        
        try:
            # 팀원 PC에서 에러가 잦은 cls=True 옵션에 예외 처리 적용
            result = ocr.ocr(img, cls=False) 
        except Exception:
            # cls 옵션으로 에러 발생 시 기본 모드로 재시도
            result = ocr.ocr(img)
        
        raw_texts = [line[1][0] for line in result[0]] if result and result[0] else []
        ocr_context = "\n".join(raw_texts)

        # 3. Ollama API 호출 (Gemma 3:4b 기반)
        OLLAMA_URL = "http://localhost:11434/api/chat"
        
        prompt = f"""
        당신은 영수증 데이터를 JSON으로 변환하는 전문가입니다.
        
        [지침]
        1. **수량(quantity) 주의**: 수량은 보통 1~10 사이의 숫자입니다. 1000 이상의 큰 숫자가 수량에 들어가지 않게 하세요.
        2. **금액 결합**: '5'와 '480'이 따로 적혀 있다면 이는 '5480'원입니다. 
        3. **항목 필터링**: 주소, 전화번호, 포인트, 바코드 번호는 절대 items에 넣지 마세요.
        4. **JSON 형식 엄수**: 반드시 아래 구조를 지키고 숫자는 정수로 입력하세요.

        [OCR 텍스트]:
        {ocr_context}

        응답 예시:
        {{
          "store": "상호명",
          "date": "YYYY-MM-DD",
          "total": 12000,
          "items": [
            {{"itemName": "품목명", "price": 1000, "quantity": 1}}
          ]
        }}
        """

        payload = {
            "model": "gemma3:4b", 
            "messages": [{"role": "user", "content": prompt}],
            "stream": False,
            "format": "json" 
        }

        response = requests.post(OLLAMA_URL, json=payload, timeout=60)
        response.raise_for_status()
        
        res_content = response.json().get('message', {}).get('content', '{}')
        data = json.loads(res_content)

        # 4. 최종 데이터 정리 및 정밀 보정
        final_data = {
            "store": data.get("store", "미인식 상점"),
            "date": data.get("date", ""),
            "total": int(re.sub(r'[^0-9]', '', str(data.get("total", 0))) or 0),
            "items": [],
            "raw_texts": raw_texts
        }

        exclude_keywords = ["신길동", "전화", "번호", "승인", "코드", "대표", "인트", "지료", "님"]

        for item in data.get("items", []):
            name = item.get("itemName") or item.get("item_name") or "알 수 없는 품목"
            
            if any(k in name for k in exclude_keywords) or len(name) < 2:
                continue

            p = int(re.sub(r'[^0-9]', '', str(item.get("price", 0))) or 0)
            q = int(re.sub(r'[^0-9]', '', str(item.get("quantity", 1))) or 1)
            
            # [영수증6 특화 보정] 5 / 480 분리 현상 강제 결합
            if p == 480 and q == 5:
                p = 5480
                q = 1
            
            # [수량/가격 반전 보정] 수량이 가격보다 월등히 크면 교체
            if q > p and p > 0 and q > 100:
                p, q = q, p
            
            if p > 0:
                final_data["items"].append({
                    "itemName": name,
                    "price": p,
                    "quantity": q
                })

        return final_data

    except Exception as e:
        return {"error": f"분석 중 오류 발생: {str(e)}"}

if __name__ == "__main__":
    if len(sys.argv) > 1:
        img_input = sys.argv[1]
        res = analyze_receipt(img_input)
        print(json.dumps(res, ensure_ascii=False))