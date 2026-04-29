/**
 * [통합 알림 시스템]
 * 1. FCM: 브라우저 푸시 토큰 관리
 * 2. SSE: 서비스 내 실시간 알림 수신
 * 3. UI: 알림 모달 및 배지 제어
 */

if (typeof eventSource === 'undefined') {
    var eventSource = null; 
}

/* --- [1] FCM 푸시 알림 제어 --- */

// 알림 스위치 토글 함수
function toggleFcm(element) {
    if (element.checked) {
        // 스위치 ON 시 브라우저 권한을 요청하고 토큰을 서버에 저장(SAVE)합니다.
        requestAndSaveToken();
    } else {
        // 스위치 OFF 시 서버에서 해당 기기의 토큰을 삭제(DELETE)하여 발송 대상에서 제외
        messaging.getToken().then(token => {
            if (token) sendTokenToServer(token, 'DELETE');
        });
    }
}

function requestAndSaveToken() {
    Notification.requestPermission().then(permission => {
        if (permission === 'granted') {
            messaging.getToken({ vapidKey: 'BHb0Mo70EYLStz5dp2P81xed_x-4SLcPKrzNzutsO3lYnyvG5_BVKgxmXd8snk2RJdqUFqOi-_raHLwRUP2BeyQ' }) 
                .then(token => {
                    if (token) sendTokenToServer(token, 'SAVE');
                });
        } else {
            alert("알림 권한이 거부되었습니다. 브라우저 설정에서 허용해주세요.");
            document.getElementById('fcmSwitch').checked = false;
        }
    });
}

//서버에 토큰 및 uuid 보냄
function sendTokenToServer(token, mode) {
    const url = mode === 'SAVE' ? '/api/fcm/save-token' : '/api/fcm/delete-token';

	const uuid = localStorage.getItem('device_id');
	
	console.log("🚀 서버로 보낼 데이터 확인 -> token:", token);
	
    authFetch(url, {
        method: 'POST',
        headers: {
			'Content-Type': 'application/json',
		},
        body: JSON.stringify({
			uuid: uuid,
			fcmToken: token 
		}),
		credentials: 'include'
    }).then(res => {
        if (res.ok) {
            const msg = mode === 'SAVE' ? "알림이 설정되었습니다." : "알림이 해제되었습니다.";
            console.log(msg);
        } else {
			console.error("401 발생 상태 코드 : " + res.status);
			throw new Error();
		}
    }).catch(err => console.error("FCM 서버 통신 에러:", err));
}

/* --- SSE 실시간 알림 수신 --- */
function connectSSE(userId) {
    if (typeof eventSource !== 'undefined' && eventSource != null) {
		eventSource.close();
	}
	
	console.log("SSE 연결 시작: UserID " + userId);
	
    //  서버와 지속적인 연결을 맺고 실시간 이벤트를 대기
    eventSource = new EventSource(`/api/notifications/subscribe?userId=${userId}`);

    eventSource.addEventListener("notification", function(event) {
        const notiData = JSON.parse(event.data);
        // 서버에서 전송한 신규 알림 데이터를 UI에 즉시 반영
        displayNotification(notiData);
        updateUnreadBadge(true); // 알림 배지 활성화
    });

    eventSource.onerror = () => {
        console.log("SSE 재연결 시도 중...");
        if (eventSource) {
			eventSource.close();
		}
		eventSource = null;
    };
}

/* --- 알림 UI 및 모달 제어 --- */

// 알림 목록 로드 (모달 열 때 호출)
function openNotiModal() {
    const modal = document.getElementById('noti');
    if (modal) {
        modal.classList.remove('hidden');
        loadNotification(); // [수정됨] 모달이 열리는 시점에 DB에서 과거 알림 내역을 읽어옵니다.
    }
}

//모달 닫기
function closenoti() {
    document.getElementById('noti').classList.add('hidden');
	location.reload();
}

//알림 불러오기
function loadNotification() {
    const notiList = document.getElementById('notification-list');
    notiList.innerHTML = '<div class="p-10 text-center text-slate-400 text-xs">로드 중...</div>';

    authFetch('/api/notifications/list')
        .then(res => res.json())
        .then(data => {
            notiList.innerHTML = '';
            if (data.length === 0) {
                notiList.innerHTML = '<div class="p-10 text-center text-slate-400 text-xs">알림이 없습니다.</div>';
            } else {
                // DB에서 가져온 리스트를 반복문을 통해 화면에 렌더링합니다.
                data.forEach(noti => displayNotification(noti));
            }
        });
}

function displayNotification(noti) {
    const notiList = document.getElementById('notification-list');
    const listItem = document.createElement('div');
    
    // 읽지 않은 알림은 강조 배경색(bg-primary/5)을 부여하여 구분합니다.
    const isUnread = noti.isRead === 'N';
    listItem.className = `p-5 border-b border-slate-50 dark:border-slate-800 cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors ${isUnread ? 'bg-primary/5' : ''}`;

    // 알림 클릭 시 정산 페이지 이동 (타입에 맞는 라우팅)
    if (noti.targetType) {
        listItem.onclick = () => markAsRead(noti.notiId, noti.targetType, noti.targetId);
    }
    listItem.innerHTML = `
        <div class="flex gap-4">
            <div class="size-2 mt-2 rounded-full ${isUnread ? 'bg-primary' : 'bg-transparent'}"></div>
            <div class="flex flex-col gap-1">
                <p class="text-sm font-medium text-slate-800 dark:text-slate-200">${noti.message}</p>
                <span class="text-[10px] text-slate-400">${noti.createdAt || ''}</span>
            </div>
        </div>
    `;
    notiList.appendChild(listItem);
}


// 개별 알림 읽음 처리 및 이동
function markAsRead(notiId, type, id) {
    authFetch(`/api/notifications/read/${notiId}`, { method: 'POST' })
        .then(() => {
            // 알림 종류(targetType)에 따라 동적으로 상세 페이지 URL을 생성하여 이동합니다.
            if (type === 'SETTLEMENT') {
                location.href = `/group/${id}/settlement`;
            } else {
                location.href = `/${type}/detail?id=${id}`;
            }
        });
}

// 모두 읽음 처리
function readall() {
    authFetch('/api/notifications/read-all', { method: 'POST' })
        .then(res => {
            if (res.ok) {
                loadNotification(); // 목록 새로고침
                updateUnreadBadge(false); // 배지 숨김
				
				const redLight = document.getElementById('red_light');
				if(redLight) {
					redLight.classList.add('hidden');
				}
            }
        });
}

function updateUnreadBadge(show) {
    const badge = document.getElementById('noti-badge');
    if (badge) {
        show ? badge.classList.remove('hidden') : badge.classList.add('hidden');
    }
}