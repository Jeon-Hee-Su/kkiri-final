

///////////////////// 이거 위치 옮기면 안돼유

// Firebase 라이브러리 가져오기
importScripts('https://www.gstatic.com/firebasejs/9.0.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/9.0.0/firebase-messaging-compat.js');

// 서비스 워커용 Firebase 설정 (본인의 키로 교체)
const FcmCon = {
    apiKey: "AIzaSyD2c1bWwo7SfUqOrYsNLO9I9E7Jd9-q-gQ",
	authDomain: "kkiri-64757.firebaseapp.com",
    projectId: "kkiri-64757",
	storageBucket: "kkiri-64757.firebasestorage.app",
    messagingSenderId: "432933470365",
    appId: "1:432933470365:web:4574a4150ad7b6bc4b5474"
};

	firebase.initializeApp(FcmCon);
	
	
	const messaging = firebase.messaging();
	
	// 백그라운드 알림 수신 처리
	messaging.onBackgroundMessage((payload) => {
	    console.log('[sw.js] 백그라운드 메시지 수신:', payload);
	    
	    // 알림 데이터 추출 (안전한 옵셔널 체이닝 사용)
	    const title = (payload.notification && payload.notification.title) ? payload.notification.title : '우리끼리 알림';
	    const body = (payload.notification && payload.notification.body) ? payload.notification.body : '새로운 알림이 도착했습니다.';
	    
	    // 알림 옵션 설정
	    const notificationOptions = {
	        body: body,
	        icon: '/resources/img/default_profile.jpg', // 아이콘 경로 확인 필수
	        badge: '/resources/img/default_profile.jpg', // 상태표시줄 작은 아이콘
	    };

	    // 브라우저 시스템 알림 띄우기
	    return self.registration.showNotification(title, notificationOptions);
	});

	self.addEventListener('push', function(event) {
	    console.log('[Service Worker] Push 이벤트 수신');
	    
	    let title = '우리끼리 알림';
	    let body = '새로운 메시지가 도착했습니다.';
	    let data = {};

	    if (event.data) {
	        try {
	            data = event.data.json();
	            // [연결 수정] 실제 FCM 서버 데이터 구조(notification)를 우선적으로 확인
	            if (data.notification) {
	                title = data.notification.title || title;
	                body = data.notification.body || body;
	            } else {
	                // 데브툴에서 직접 {"title": "...", "body": "..."} 형태로 보냈을 경우
	                title = data.title || title;
	                body = data.body || body;
	            }
	        } catch (e) {
	            // JSON이 아닌 일반 텍스트로 올 경우
	            body = event.data.text();
	        }
	    }

	    const options = {
	        body: body,
	        icon: '/resources/img/default_profile.jpg',
	        badge: '/resources/img/default_profile.jpg',
	    };

	    // 알림을 띄우는 명령
	    event.waitUntil(self.registration.showNotification(title, options));
	});

	// 알림을 클릭했을 때 사라지게
	self.addEventListener('notificationclick', function(event) {
	    event.notification.close();
	});

