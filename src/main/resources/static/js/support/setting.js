/**
 * 우리끼리(kkiri) 설정 페이지 통합 스크립트
 */


const firebaseConfig = {
		    apiKey: "AIzaSyD2c1bWwo7SfUqOrYsNLO9I9E7Jd9-q-gQ",
		    authDomain: "kkiri-64757.firebaseapp.com",
		    projectId: "kkiri-64757",
		    storageBucket: "kkiri-64757.firebasestorage.app",
		    messagingSenderId: "432933470365",
		    appId: "1:432933470365:web:4574a4150ad7b6bc4b5474"
		  };

firebase.initializeApp(firebaseConfig);
const messaging = firebase.messaging();

if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/firebase-messaging-sw.js')
    .then(function(registration) {
        console.log('서비스 워커 등록 성공:', registration.scope);
    }).catch(function(err) {
        console.log('서비스 워커 등록 실패:', err);
    });
}

/* --- [1] 이용약관 및 정책 모달 제어 --- */
function openTermsModal() {
    const modal = document.getElementById('termsModal');
    if (modal) {
        modal.classList.remove('hidden');
        document.body.style.overflow = 'hidden'; // 배경 스크롤 방지
    }
}

function closeTermsModal() {
    const modal = document.getElementById('termsModal');
    if (modal) {
        modal.classList.add('hidden');
        document.body.style.overflow = 'auto'; // 배경 스크롤 허용
    }
}

/* --- [2] 회원 탈퇴 확인 모달 제어 --- */
function confirmWithdrawal() {
    const modal = document.getElementById('withdrawModal');
    if (modal) {
        modal.classList.remove('hidden');
        document.body.style.overflow = 'hidden';
    }
}

function closeWithdrawModal() {
    const modal = document.getElementById('withdrawModal');
    if (modal) {
        modal.classList.add('hidden');
        document.body.style.overflow = 'auto';
    }
}

/* --- [3] 최종 탈퇴 실행 (서버 통신 및 정산 체크) --- */
function executeWithdrawal() {
    // 1. 버튼 중복 클릭 방지 처리 (이벤트 발생 요소 찾기)
    const btn = event ? (event.target.closest('button') || event.target) : null;
    let originalText = "탈퇴하기";
    
    if (btn && btn.tagName === 'BUTTON') {
        originalText = btn.innerText;
        btn.disabled = true;
        btn.innerText = "처리 중...";
    }

    // 2. 서버 API 호출
    authFetch('/api/settings/withdraw', {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json'
            // Spring Security 사용 시 CSRF 토큰 필드 추가 필요
            // 'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]').content 
        }
    })
    .then(async response => {
        if (response.ok) {
            // Case A: 탈퇴 성공
            alert("탈퇴 처리가 완료되었습니다. 그동안 '우리끼리'를 이용해주셔서 감사합니다.");
            location.href = "/login"; // 로그인 페이지로 리다이렉트
        } else {
            // Case B: 서버에서 에러 메시지를 보낸 경우 (400 Bad Request 등)
            const errorMsg = await response.text();
            
            if (errorMsg === "UNSETTLED_AMOUNT") {
                // 미정산 금액이 남아있어 탈퇴 거부된 경우
                alert("아직 정산하지 않은 금액이 남아있습니다.\n모든 정산을 완료한 후 탈퇴가 가능합니다.");
                location.href = "group/group-settings"; // 정산 내역 페이지로 이동
            } else {
                // 기타 서버 오류
                alert("탈퇴 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
                if (btn) {
                    btn.disabled = false;
                    btn.innerText = originalText;
                }
            }
        }
    })
    .catch(error => {
        // Case C: 네트워크 통신 자체 실패
        console.error('Error:', error);
        alert("통신 오류가 발생했습니다. 네트워크 상태를 확인해주세요.");
        if (btn) {
            btn.disabled = false;
            btn.innerText = originalText;
        }
    });
}

/* --- [4] 알림 토글 설정 저장 (필요 시 사용) --- */
function updateNotification(type, isEnabled) {
    authFetch('/api/settings/notification', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: type, enabled: isEnabled })
    })
    .then(res => {
        if (!res.ok) throw new Error();
        console.log(`${type} 알림 상태 변경 완료`);
    })
    .catch(() => alert("알림 설정 저장에 실패했습니다."));
}

/* --- [5] 전역 이벤트: 배경 클릭 시 모달 닫기 --- */
window.addEventListener('click', (e) => {
    const termsModal = document.getElementById('termsModal');
    const withdrawModal = document.getElementById('withdrawModal');
    
    // 클릭된 타겟이 모달의 '배경(딤드 처리된 부분)'일 때만 닫기
    if (e.target === termsModal) closeTermsModal();
    if (e.target === withdrawModal) closeWithdrawModal();
});

// 알림 토글에 따른 알림 받기
function toggleFcm(element) {
	if(element.checked) {
		requestAndSaveToken();
	} else {
		//deleteTokenFromServer();
	}
}

// 토큰 로직 적용
function requestAndSaveToken() {
	Notification.requestPermission().then(permissions => {
		if(permissions === 'granted') {
			messaging.getToken({vapidKey: 'BHb0Mo70EYLStz5dp2P81xed_x-4SLcPKrzNzutsO3lYnyvG5_BVKgxmXd8snk2RJdqUFqOi-_raHLwRUP2BeyQ'}).then(token => {
				sendTokenToServer(token, 'SAVE');
			});
		} else {
			alert("브라우저 알림 권한을 허용해주세요.");
			document.getElementById('fcmSwitch').checked = false;
			
		}
	});
}


