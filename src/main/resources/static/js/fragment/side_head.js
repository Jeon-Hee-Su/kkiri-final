// ── auth 페이지에서는 토큰 관련 기능 전체 비활성화 ──
const _IS_AUTH_PAGE = window.location.pathname.startsWith('/auth/');

// 1. 사이드바 토글 함수
function toggleSidebar() {
    const sidebar = document.getElementById('main-sidebar');
    const overlay = document.getElementById('sidebar-overlay');
    
    if (sidebar.classList.contains('-translate-x-full')) {
        sidebar.classList.remove('-translate-x-full');
        overlay.classList.remove('hidden');
    } else {
        sidebar.classList.add('-translate-x-full');
        overlay.classList.add('hidden');
    }
}

// 2. 통합 로그아웃 함수
async function handleLogout() {
	stopSseSessionCheck();
    if (confirm("로그아웃 하시겠습니까?")) {
        try {
            const response = await authFetch('/api/auth/logout', { 
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include'
            });
            if (response.ok || response.redirected) {
                window.location.href = "/"; 
            }
        } catch (error) {
            console.error("로그아웃 통신 오류:", error);
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// 토큰 카운트다운 & 모달 모듈
// ─────────────────────────────────────────────────────────────────────────

let _tokenCountdownInterval = null;
let _currentRemaining       = 0;
let _warningShown           = false; // 1분 경고 모달을 한 번만 띄우기 위한 플래그
let _userDeclinedExtension  = false; // "나중에" 눌렀는지 여부 → 자동 갱신 차단

/** 남은 초 → MM:SS */
function _formatSeconds(sec) {
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
}

/** 뱃지 색상: 초록(>5분) / 노랑(1~5분) / 빨강(<1분) */
function _updateBadgeColor(remaining) {
    const badge = document.getElementById('token-timer-badge');
    if (!badge) return;
    badge.classList.remove(
        'bg-emerald-100','text-emerald-700','dark:bg-emerald-900/40','dark:text-emerald-400',
        'bg-yellow-100','text-yellow-700','dark:bg-yellow-900/40','dark:text-yellow-400',
        'bg-red-100','text-red-700','dark:bg-red-900/40','dark:text-red-400'
    );
    if (remaining > 300) {
        badge.classList.add('bg-emerald-100','text-emerald-700','dark:bg-emerald-900/40','dark:text-emerald-400');
    } else if (remaining > 60) {
        badge.classList.add('bg-yellow-100','text-yellow-700','dark:bg-yellow-900/40','dark:text-yellow-400');
    } else {
        badge.classList.add('bg-red-100','text-red-700','dark:bg-red-900/40','dark:text-red-400');
    }
}

// ── 경고 모달 안 시간 동기화 (매초 호출) ─────────────────────────────────
function _syncWarningModalTime() {
    const modal = document.getElementById('token-warning-modal');
    if (!modal || modal.classList.contains('hidden')) return;
    const txt = document.getElementById('warning-remaining-text');
    if (txt) txt.textContent = _formatSeconds(Math.max(0, _currentRemaining));
}

// ── 1분 전 경고 모달 ──────────────────────────────────────────────────────
function showTokenWarningModal(remainSec) {
    const modal = document.getElementById('token-warning-modal');
    const txt   = document.getElementById('warning-remaining-text');
    if (!modal) return;
    if (txt) txt.textContent = _formatSeconds(remainSec);
    modal.classList.remove('hidden');
}

/** "나중에" 클릭 → 모달 닫고 자동 갱신 차단 플래그 ON */
function closeTokenWarningModal() {
    const modal = document.getElementById('token-warning-modal');
    if (modal) modal.classList.add('hidden');
    _userDeclinedExtension = true; // ← 핵심: 이후 만료 시 자동 갱신 안 함
}

/** "로그인 연장하기" 클릭 → refresh 즉시 실행 */
async function extendSession() {
    const modal = document.getElementById('token-warning-modal');
    if (modal) modal.classList.add('hidden');
    _userDeclinedExtension = false; // 연장 의사 있음

    try {
        const res = await authFetch('/api/auth/refresh', { method: 'POST', credentials: 'include' });
        if (res.ok) {
            console.info('✅ 세션 연장 성공');
            _warningShown = false;
            startTokenCountdown();
        } else {
            await doExpiredLogout();
        }
    } catch (e) {
        console.error('세션 연장 실패:', e);
        await doExpiredLogout();
    }
}

// ── 만료 처리: 서버 로그아웃 + 만료 모달 표시 ───────────────────────────
async function doExpiredLogout() {
    if (_tokenCountdownInterval) {
        clearInterval(_tokenCountdownInterval);
        _tokenCountdownInterval = null;
    }

    const badge = document.getElementById('token-timer-badge');
    if (badge) badge.classList.add('hidden');

    if (window.location.pathname.startsWith('/auth/')) return;

    // ✅ 서버에 쿠키 있는지 직접 확인 (타이밍 문제 없음)
    try {
        const res = await authFetch('/api/auth/token-status', { method: 'POST', credentials: 'include' });
        const data = await res.json();
        // 토큰이 없거나 이미 만료된 상태면 → 그냥 로그인 페이지로 이동 (모달 없이)
        if (!data.valid) {
            window.location.replace('/auth/login');
            return;
        }
    } catch (e) {
        window.location.replace('/auth/login');
        return;
    }

    try {
        await authFetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
    } catch (e) {}

    const modal = document.getElementById('token-expired-modal');
    if (modal) modal.classList.remove('hidden');
}

function closeExpiredModal() {
    const modal = document.getElementById('token-expired-modal');
    if (modal) modal.classList.add('hidden');
    // ✅ '/'로 보내면 다시 인증체크 → 무한루프. 로그인 페이지로 직접 이동
    window.location.replace('/auth/login');
}

// ── 카운트다운 메인 ────────────────────────────────────────────────────────
async function startTokenCountdown() {
    // 기존 인터벌 제거
    if (_tokenCountdownInterval) {
        clearInterval(_tokenCountdownInterval);
        _tokenCountdownInterval = null;
    }
    _warningShown          = false;
    _userDeclinedExtension = false;

    // 서버에서 남은 시간 조회
    try {
        const res = await authFetch('/api/auth/token-status', { method: 'POST', credentials: 'include' });
        if (!res.ok) return;
        const data = await res.json();
        _currentRemaining = data.remainingSeconds || 0;
    } catch (e) { return; }

    const timerText = document.getElementById('token-timer-text');
    const badge     = document.getElementById('token-timer-badge');

    if (!timerText || _currentRemaining <= 0) return;
    if (badge) badge.classList.remove('hidden');

    timerText.textContent = _formatSeconds(_currentRemaining);
    _updateBadgeColor(_currentRemaining);

    _tokenCountdownInterval = setInterval(async () => {
        _currentRemaining--;

        // ── 1분 이하 진입 시 경고 모달 (한 번만) ──
        if (_currentRemaining <= 60 && _currentRemaining > 0 && !_warningShown) {
            _warningShown = true;
            showTokenWarningModal(_currentRemaining);
        }

        // ── 만료 ──
        if (_currentRemaining <= 0) {
            clearInterval(_tokenCountdownInterval);
            _tokenCountdownInterval = null;

            // 경고 모달이 떠있거나 "나중에"를 눌렀으면 → 바로 로그아웃
            const warningModal = document.getElementById('token-warning-modal');
            const modalVisible = warningModal && !warningModal.classList.contains('hidden');

            if (_userDeclinedExtension || modalVisible) {
                console.warn('⛔ 모달 무시 or 연장 거부 → 만료 로그아웃');
                await doExpiredLogout();
            } else {
                // 자동 갱신 시도
                if (timerText) timerText.textContent = '갱신 중...';
                try {
                    const refreshRes = await authFetch('/api/auth/refresh', { method: 'POST', credentials: 'include' });
                    if (refreshRes.ok) {
                        console.info('✅ Access Token 자동 갱신 성공');
                        startTokenCountdown();
                    } else {
                        console.warn('⛔ Refresh Token 만료 → 로그아웃');
                        await doExpiredLogout();
                    }
                } catch (e) {
                    console.error('토큰 갱신 요청 실패:', e);
                    await doExpiredLogout();
                }
            }
            return;
        }

        if (timerText) timerText.textContent = _formatSeconds(_currentRemaining);
        _updateBadgeColor(_currentRemaining);
        _syncWarningModalTime();
    }, 1000);
}

// 3. 헤더 UI 업데이트 함수
async function updateHeaderUI() {
    if (_IS_AUTH_PAGE) return;  // auth 페이지에서는 실행 안 함
    const currentPath   = window.location.pathname;
    const userHeader    = document.getElementById('user-profile-area');
    const guestHeader   = document.getElementById('guest-header');
    const authContainer = document.getElementById('auth-buttons');

    if (currentPath.includes('/signup')) {
        [userHeader, guestHeader, authContainer].forEach(el => el?.classList.add('hidden'));
        return; 
    }

    const nameDisplay     = document.getElementById('header-user-name');
    const profileImg      = document.getElementById('header-profile-img');
    const sidebarLoginBtn = document.getElementById('sideloginbtn');
    const sidebarLogoutBtn= document.getElementById('sidelogoutbtn');

    try {
        const response = await authFetch('/api/user/profile/details', {
            cache: 'no-store',
            credentials: 'include'
        });
        const contentType = response.headers.get("content-type");
        
        if (!response.ok || (contentType && contentType.includes("text/html"))) {
            throw new Error("Not logged in or invalid response");
        }

        const userData = await response.json();
        
        guestHeader?.classList.add('hidden');
        if (userHeader) {
            userHeader.classList.remove('hidden');
            userHeader.classList.add('flex');
        }

        if (nameDisplay) nameDisplay.innerText = userData.nickname || userData.name || "사용자";
        if (profileImg && userData.profileImage) {
            profileImg.style.backgroundImage = `url('${userData.profileImage}')`;
        }

        sidebarLoginBtn?.classList.add('hidden');
        if (sidebarLogoutBtn) {
            sidebarLogoutBtn.classList.remove('hidden');
            sidebarLogoutBtn.classList.add('flex');
            sidebarLogoutBtn.onclick = handleLogout;
        }

        if (authContainer) {
            authContainer.innerHTML = `
                <a href="javascript:void(0)" onclick="handleLogout()" class="flex flex-col items-center justify-center min-w-[48px] group cursor-pointer">
                    <span class="material-symbols-outlined text-[20px] text-red-600">logout</span>
                    <p class="text-xs font-semibold !text-red-600 leading-none">로그아웃</p>
                </a>
            `;
        }

        // 로그인 확인 후 카운트다운 시작
        startTokenCountdown();
		startSseSessionCheck();

    } catch (error) {
        if (guestHeader) guestHeader.classList.remove('hidden');
        if (userHeader) {
            userHeader.classList.add('hidden');
            userHeader.classList.remove('flex');
        }
        
        if (sidebarLoginBtn) {
            sidebarLoginBtn.classList.remove('hidden');
            sidebarLoginBtn.classList.add('flex');
        }
        if (sidebarLogoutBtn) sidebarLogoutBtn.classList.add('hidden');

        if (authContainer) {
            authContainer.innerHTML = `
                <a href="/auth/login" class="flex flex-col items-center justify-center min-w-[48px] group">
                    <span class="material-symbols-outlined text-[20px] text-green-600">login</span>
                    <p class="text-xs font-semibold !text-green-600 leading-none">로그인</p>
                </a>
            `;
        }
    }
}


// 4. 초기화
document.addEventListener('DOMContentLoaded', () => {
    // auth 페이지(로그인/회원가입)에서는 헤더 UI 업데이트 불필요
    if (window.location.pathname.startsWith('/auth/')) return;
    updateHeaderUI();
	window.syncbadgeWithDB();
});

// redlight
 window.syncbadgeWithDB = function() {
	const bage = document.getElementById('red_light');
	if(!bage) return;
	
	authFetch('/api/notifications/read-status')
		.then(res => res.json()) 
		.then(data => {
			if(data && data.hasUnread === true) {
				bage.classList.add('flex');
				bage.classList.remove('hidden');
			} else {
				bage.classList.add('hidden');
				bage.classList.remove('flex');
			}
		});
}

// 5. 알림 모달 토글 함수
function opennoti() {
    const modal   = document.getElementById('noti');
    const overlay = document.getElementById('sidebar-overlay');
    
    if (modal) {
        modal.classList.remove('hidden');
        overlay.classList.remove('hidden');
        if (typeof loadNotification === 'function') {
            loadNotification();
        }
    }
     
    const badge = document.getElementById('noti-badge');
    if (badge) badge.classList.add('hidden');
}

async function loadUserProfile() {
    const response = await fetchWithAuth('/api/user/profile/details');
    const userData = await response.json();
    if (userData && userData.picture) {
        document.querySelector('.user-avatar').src = userData.picture;
    }
}

// ── SSE 실시간 세션 감지 ──────────────────────────
let _sseSource = null;
let _sseReconnectTimer = null; // 재연결 타이머 추적용

async function forceLogout(message) {
    // 재연결 타이머 취소 (로그아웃 후 재연결 시도 방지)
    if (_sseReconnectTimer) {
        clearTimeout(_sseReconnectTimer);
        _sseReconnectTimer = null;
    }

    if (_tokenCountdownInterval) {
        clearInterval(_tokenCountdownInterval);
        _tokenCountdownInterval = null;
    }

    /*try {
        await authFetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
    } catch (e) {}*/

    alert(message || '다른 기기에서 로그인되어 자동 로그아웃됩니다.');
    window.location.replace('/auth/login');
}

function startSseSessionCheck() {
    // 이미 연결 중이면 중복 방지
    if (_sseSource) return;

    // auth 페이지에서는 SSE 연결 안함
    if (window.location.pathname.startsWith('/auth/')) return;

    _sseSource = new EventSource('/api/auth/session-stream', { withCredentials: true });

    _sseSource.addEventListener('force-logout', (e) => {
        stopSseSessionCheck(); // SSE 먼저 닫고
        forceLogout(e.data);   // 로그아웃
    });

    _sseSource.addEventListener('connected', () => {
        console.info('🔐 실시간 세션 감지 시작');
    });

    // QR 결제 완료 이벤트 수신 → QR 모달 화면 갱신
    _sseSource.addEventListener('qr-payment-complete', (e) => {
        const parts = e.data.split(',');
        const deducted  = parseInt(parts[0]);
        const remaining = parseInt(parts[1]);

        // QR 코드 숨기고 결제 완료 화면으로 교체
        const qrContainer = document.getElementById('qr-code-container');
        const qrTimer     = document.getElementById('qr-timer');
        const qrLoading   = document.getElementById('qr-loading');
        const qrExpired   = document.getElementById('qr-expired-msg');
        const balanceText = document.getElementById('qr-balance-text');

        if (!qrContainer) return; // QR 모달이 열려있지 않으면 무시

        // 카운트다운 인터벌 정지 (group-main.js의 전역변수 접근)
        if (typeof qrCountdownInterval !== 'undefined' && qrCountdownInterval) {
            clearInterval(qrCountdownInterval);
            qrCountdownInterval = null;
        }

        // QR 코드 영역을 결제 완료 메시지로 교체
        if (qrContainer) qrContainer.classList.add('hidden');
        if (qrTimer)     qrTimer.classList.add('hidden');
        if (qrLoading)   qrLoading.classList.add('hidden');
        if (qrExpired)   qrExpired.classList.add('hidden');

        // 잔액 업데이트
        if (balanceText) balanceText.textContent = Number(remaining).toLocaleString() + '원';

        // 결제 완료 메시지 삽입
        const paymentModal = document.getElementById('paymentModal');
        if (paymentModal) {
            // 기존 완료 메시지가 있으면 제거
            const existing = document.getElementById('qr-complete-msg');
            if (existing) existing.remove();

            const msg = document.createElement('div');
            msg.id = 'qr-complete-msg';
            msg.className = 'w-44 h-44 flex flex-col items-center justify-center gap-2 bg-emerald-50 rounded-2xl mx-auto';
            msg.innerHTML = `
                <span class="material-symbols-outlined text-5xl text-emerald-500">check_circle</span>
                <p class="font-black text-emerald-600 text-sm">결제 완료!</p>
                <p class="text-xs text-slate-500 font-bold">-${Number(deducted).toLocaleString()}원</p>
            `;
            if (qrContainer && qrContainer.parentNode) {
                qrContainer.parentNode.insertBefore(msg, qrContainer);
            }
        }
    });

    _sseSource.onerror = () => {
        // auth 페이지로 이동한 경우 재연결 시도 안함
        if (window.location.pathname.startsWith('/auth/')) {
            stopSseSessionCheck();
            return;
        }

        console.warn('SSE 연결 끊김, 3초 후 재연결 시도...');
        _sseSource.close();
        _sseSource = null;

        // 재연결 타이머 중복 방지
        if (_sseReconnectTimer) clearTimeout(_sseReconnectTimer);
        _sseReconnectTimer = setTimeout(() => {
            _sseReconnectTimer = null;
            startSseSessionCheck();
        }, 3000);
    };
}

function stopSseSessionCheck() {
    // 재연결 타이머도 같이 취소
    if (_sseReconnectTimer) {
        clearTimeout(_sseReconnectTimer);
        _sseReconnectTimer = null;
    }
    if (_sseSource) {
        _sseSource.close();
        _sseSource = null;
    }
}