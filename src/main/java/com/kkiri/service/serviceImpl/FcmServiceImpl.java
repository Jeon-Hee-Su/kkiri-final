package com.kkiri.service.serviceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.kkiri.mapper.FcmMapper;
import com.kkiri.service.FcmService;

@Service
public class FcmServiceImpl implements FcmService {
	
	@Autowired
    private FcmMapper fcmMapper;

	@Override
	public void sendPushToUser(int userId, String title, String body) {
		List<String> tokens = fcmMapper.findAllTokensByUserId(userId);
		
		if (tokens == null || tokens.isEmpty()) {
            System.err.println("설정에서 알림 설정을 확인해 주세요");
            return;
        }
		
		if(tokens != null && !tokens.isEmpty()) {
			for(String deviceToken : tokens) {
				Message message = Message.builder()
						.setToken(deviceToken) // 인자로 뽑아낸 각 기기의 토큰을 세팅
						.setNotification(Notification.builder()
								.setTitle(title)
								.setBody(body)
								.build())
						.build();
				
				try {
					FirebaseMessaging.getInstance().send(message);
				} catch (FirebaseMessagingException e) {
					String errorCode = e.getMessagingErrorCode().name();
					
					if("UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode)) {
						// 앱이 삭제되었거나 알림 권한차단시 			토큰형식 에러
						
						fcmMapper.deleteToken(deviceToken);
						System.out.println("유효하지 않은 토큰 삭제 : " + deviceToken);
					} else {
						System.out.println("FCM 일시적 오류" + errorCode);
					}
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	@Override
	public void saveToken(int userId, String fcmToken, String uuid) {
		fcmMapper.saveOrUpdateToken(userId, fcmToken, uuid);
	}
	
	@Override
	public void deleteToken(String fcmToken) {
		fcmMapper.deleteToken(fcmToken);
	}

}


