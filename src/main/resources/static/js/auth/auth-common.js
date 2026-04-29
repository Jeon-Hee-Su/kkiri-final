/**
 * auth-common.js (통합본: 순수 쿠키 방식 전용 페이지 라우팅 및 공통 인증)
 */

(async function initAuthCheck() {
    // ❌ [삭제됨] 쿠키를 읽어와 localStorage에 동기화하던 취약점 로직 완전 삭제

    const currentPath = window.location.pathname;
    const urlParams = new URLSearchParams(window.location.search);
    const urlToken = urlParams.get('token');

    // 1. 소셜 로그인 후 URL에 토큰이 노출되는 경우 처리
    if (urlToken) {
        // ❌ [삭제됨] localStorage.setItem('accessToken', urlToken);

        // ✅ 쿠키에만 저장 (Path=/ 설정을 꼭 해줘야 모든 페이지에서 공유됩니다)
        // 참고: 가장 완벽한 보안은 백엔드가 Set-Cookie로 주는 것이지만, 
        // URL로 넘어온 경우를 대비해 프론트단에서 쿠키를 굽는 최소한의 로직만 남깁니다.
        document.cookie = `accessToken=${urlToken}; path=/; max-age=3600; SameSite=Lax`;
        
        console.log("로그인 성공: 토큰을 쿠키에만 저장했습니다.");

        // URL 파라미터 제거 (깔끔하게 만들기)
        const cleanUrl = window.location.origin + currentPath;
        window.history.replaceState({}, document.title, cleanUrl);
    }

    // 2. 검사 제외 목록 (로그인 없이도 볼 수 있는 공개 페이지들)
    const publicPaths = ['/', '/auth/login', '/auth/signup', '/auth/signupuser', '/index', '/join'];

    // 3. 보호된 페이지 진입 시에만 서버에 인증 상태 확인 (쿠키 자동 전송)
    if (!publicPaths.some(p => currentPath === p)) {
        try {
            const response = await fetch('/api/user/profile/details', {
                method: 'GET',
                credentials: 'include' // ★ 쿠키 전송 보장
            });

            // 4. 인증 실패(401) 또는 권한 없음(403) 시 로그인 페이지로 튕겨냄
            if (response.status === 401 || response.status === 403) {
                alert("로그인이 필요한 서비스입니다.");
                window.location.replace("/auth/login"); // 뒤로가기 꼼수 방지
            }
        } catch (error) {
            console.error("인증 확인 실패:", error);
        }
    }
})();

// 로그아웃 함수 (전역에서 호출 가능)
async function handleLogout() {
    if (!confirm("로그아웃 하시겠습니까?")) return;

    try {
        await fetch('/api/auth/logout', { 
            method: 'POST',
            credentials: 'include' // ★ 백엔드가 쿠키를 삭제할 수 있도록 요청에 포함
        });
        
        // 브라우저에 남아있을 수 있는 UI용 잔여 데이터 초기화
        sessionStorage.clear();
        localStorage.clear(); // 토큰은 없지만 기타 UI 설정값 지우기용
        
        alert("로그아웃 되었습니다.");
        window.location.replace("/"); 
    } catch (error) {
        localStorage.clear();
        console.error("로그아웃 처리 중 오류:", error);
        window.location.replace("/"); 
    }
}

// =====================================================================
// [핵심] API 요청 공용 함수 - 401 시 자동 refresh 후 재시도
// 사용법: const res = await authFetch('/api/something', { method: 'POST', ... })
// =====================================================================

// 중복 forceLogout 방지 플래그
let _isForceLoggingOut = false;
// refresh 중복 방지 플래그
let _isRefreshing = false;

async function authFetch(url, options = {}) {
    if (_isForceLoggingOut) return new Response(null, { status: 401 });

    options.credentials = 'include';
    let response = await fetch(url, options);

    if (response.status === 401) {
        if (_isRefreshing) return response;

        _isRefreshing = true;
        const refreshResult = await tryRefreshToken();
        _isRefreshing = false;

        if (refreshResult === 'refreshed') {
            response = await fetch(url, options);
        } else if (refreshResult === 'other_device') {
            forceLogout('다른 기기에서 로그인되어 자동 로그아웃됩니다.');
        } else {
            // ❌ 기존: window.location.replace('/auth/login'); ← 이게 문제!
            // ✅ 수정: 그냥 401 응답 그대로 반환, 각 호출부에서 알아서 처리
            return response;
        }
        return response;
    }

    return response;
}

// refresh token으로 access token 재발급 시도
// 반환값: 'refreshed' | 'other_device' | 'no_token'
async function tryRefreshToken() {
    try {
        const res = await fetch('/api/auth/refresh', {
            method: 'POST',
            credentials: 'include'
        });
        if (res.ok) return 'refreshed';

        if (res.status === 400) return 'no_token';      // refreshToken 쿠키 자체가 없음 (미로그인)
        if (res.status === 401) return 'other_device';  // 쿠키는 있는데 DB 불일치 (다른 기기)
        return 'no_token';
    } catch (e) {
        return 'no_token';
    }
}

// 강제 로그아웃 (다른 기기 로그인 감지 시)
function forceLogout(message) {
    if (_isForceLoggingOut) return;
    _isForceLoggingOut = true;
    sessionStorage.clear();
    localStorage.clear();
    alert(message || "세션이 만료되었습니다.");
    window.location.replace("/");
}

// 서버에 인증 상태 확인 요청 (특정 버튼 클릭 등 이벤트용)
async function checkAuthentication() {
    try {
        const response = await fetch('/api/user/profile/details', {
            method: 'GET',
            credentials: 'include'
        });

        console.log("응답 상태:", response.status, "리다이렉트 됨?:", response.redirected);

        if (response.status === 401 || response.status === 403 || 
           (response.redirected && response.url.includes('/login'))) {
            alert("로그인이 필요한 서비스입니다.");
            window.location.replace("/login"); 
            return false;
        }
        
        return true;
    } catch (error) {
        console.error("인증 확인 중 네트워크 오류:", error);
    }
}