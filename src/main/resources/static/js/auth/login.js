/**
 * KKIRI 로그인 페이지 전용 스크립트
 * (초대 코드 리다이렉트 및 권한 알림 로직 포함 버전)
 */

/**
 * 쿠키에서 값 읽기 헬퍼
 */
function getCookieValue(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
    return null;
}

document.addEventListener('DOMContentLoaded', () => {
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('needLogin') === 'true' || urlParams.get('error') === 'unauthorized') {
        setTimeout(() => {
            alert("로그인이 필요한 서비스입니다.");
            window.history.replaceState({}, document.title, window.location.pathname);
        }, 100);
    }

    console.log("login.js 로드 완료 (쿠키 기반)");

    // --- 1. 비밀번호 가시성 토글 ---
    const passwordInput = document.getElementById('passwordInput');
    const passwordToggle = document.getElementById('passwordToggle');
    const toggleIcon = document.getElementById('toggleIcon');

    if (passwordToggle && passwordInput && toggleIcon) {
        passwordToggle.addEventListener('click', () => {
            const isPassword = passwordInput.type === 'password';
            passwordInput.type = isPassword ? 'text' : 'password';
            toggleIcon.textContent = isPassword ? 'visibility_off' : 'visibility';
            if (isPassword) {
                toggleIcon.classList.add('text-primary');
            } else {
                toggleIcon.classList.remove('text-primary');
            }
        });
    }

    // --- 2. 로그인 처리 ---
    const loginBtn = document.getElementById('loginBtn');

    if (loginBtn) {
        loginBtn.addEventListener('click', async () => {
            const loginIdElement = document.getElementById('loginId');
            const passwordInputElement = document.getElementById('passwordInput');

            if (!loginIdElement || !passwordInputElement) return;

            const loginId = loginIdElement.value;
            const password = passwordInputElement.value;

            if (!loginId || !password) {
                alert("아이디와 비밀번호를 모두 입력해주세요.");
                return;
            }

            try {
                const response = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ loginId, password })
                });

                if (response.ok) {
                    // 초대 코드: 쿠키 우선, 없으면 sessionStorage 확인
                    const pendingCodeFromCookie = getCookieValue("pendingInviteCode");
                    const pendingCodeFromSession = sessionStorage.getItem("pendingInviteCode");
                    const pendingCode = pendingCodeFromCookie || pendingCodeFromSession;

                    let targetUrl = "/index";

                    if (pendingCode) {
                        // 쿠키/세션 정리
                        document.cookie = "pendingInviteCode=; path=/; max-age=0";
                        sessionStorage.removeItem("pendingInviteCode");
                        targetUrl = `/join?code=${pendingCode}`;
                    }

                    if (window.opener) {
                        window.opener.location.replace(targetUrl);
                        window.close();
                    } else {
                        window.location.replace(targetUrl);
                    }

                } else {
                    const errorMsg = await response.text();
                    alert("로그인 실패: " + (errorMsg || "아이디 또는 비밀번호가 틀렸습니다."));
                }
            } catch (error) {
                console.error("로그인 중 에러 발생:", error);
                alert("서버와 통신 중 오류가 발생했습니다.");
            }
        });
    }
});

// --- 3. 비밀번호 찾기 ---
function openchangePW() {
    const modal = document.getElementById('pwModal');
    modal.classList.remove('hidden');
}

function closePWModal() {
    const modal = document.getElementById('pwModal');
    modal.classList.add('hidden');
    document.getElementById('pwchangeform').reset();
}