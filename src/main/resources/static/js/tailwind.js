tailwind.config = {
    darkMode: "class", // 'dark' 클래스로 다크모드 제어
    theme: {
        extend: {
            colors: { 
                // 브랜드 메인 색상 및 배경색 설정
                "primary": "#135bec",
                "background-light": "#f8fafc",
                "background-dark": "#0f172a"
            },
            fontFamily: {
                // 기본 폰트 설정 유지
                "display": ["Inter", "Noto Sans KR", "sans-serif"]
            },
            // 스크린샷 UI의 둥근 디자인을 위한 반지름 설정
            borderRadius: {
                "DEFAULT": "0.25rem", 
                "lg": "0.5rem", 
                "xl": "0.75rem", 
                "2xl": "1rem",   /* 생성 버튼용 */
                "3xl": "1.5rem", /* 생성 버튼용 2 */
                "full": "9999px" /* 모임 목적 칩(Chip)용 */
            }
        }
    }
}