// 서비스 워커용 Firebase 설정
const FcmCon = {
    apiKey: "AIzaSyD2c1bWwo7SfUqOrYsNLO9I9E7Jd9-q-gQ",
	authDomain: "kkiri-64757.firebaseapp.com",
    projectId: "kkiri-64757",
	storageBucket: "kkiri-64757.firebasestorage.app",
    messagingSenderId: "432933470365",
    appId: "1:432933470365:web:4574a4150ad7b6bc4b5474"
};

// 초기화
firebase.initializeApp(FcmCon);

const messaging = firebase.messaging();


// 페이지 로드 시 토큰을 체크하고 서버로 전송하는 함수
function updateFcmToken() {
    messaging.getToken({
		vapidKey: 'BHb0Mo70EYLStz5dp2P81xed_x-4SLcPKrzNzutsO3lYnyvG5_BVKgxmXd8snk2RJdqUFqOi-_raHLwRUP2BeyQ'
	})
        .then((token) => {
            if (token) {
				console.log("토큰 뽑음 : ", token);
				
                // AJAX나 Fetch를 사용해 Java 서버의 토큰 업데이트 API 호출
               authFetch('/fcm/update-token', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ token: token })
                })
				.then(response => {
					if(response.ok) {
						console.log("서버저장 완료")
					}
				})
				}			
			});
        }
		updateFcmToken();