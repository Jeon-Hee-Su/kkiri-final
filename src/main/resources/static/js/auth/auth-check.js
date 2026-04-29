(async function() {
    const currentPath = window.location.pathname;
    const urlParams = new URLSearchParams(window.location.search);
    const urlToken = urlParams.get('token'); // [참고] 소셜 로그인 후 받는 것도 이제 쿠키로 처리 권장

    // 1. 소셜 로그인 등에서 쿠키를 구워줬다면 URL 파라미터만 정리 (토큰 저장 로직 제거)
    if (urlToken) {
        const cleanUrl = window.location.origin + currentPath;
        window.history.replaceState({}, document.title, cleanUrl);
    }

    // 2. 로그인 없이도 볼 수 있는 페이지들
    const publicPages = ['/', '/login', '/signup', '/signupuser'];
    if (publicPages.includes(currentPath)) {
        return; 
    }

    // 3. 보호된 페이지 진입 시, localStorage 체크 불가. 
    // 서버에 내 정보 통신을 날려보고 인증 실패(401)가 뜨면 튕겨냅니다.
    try {
        const response = await fetch('/api/user/profile/details', { 
            method: 'GET',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include' // 쿠키 전송
        });

        if (!response.ok) {
            throw new Error("Unauthorized");
        }
    } catch (error) {
        alert("로그인이 필요한 서비스입니다.");
        window.location.href = "/login?needLogin=true";
    }
})();