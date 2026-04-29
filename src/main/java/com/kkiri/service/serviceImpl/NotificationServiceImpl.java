package com.kkiri.service.serviceImpl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.kkiri.mapper.NotificationMapper;
import com.kkiri.model.vo.NotificationVO;
import com.kkiri.service.NotificationService;

@Service
public class NotificationServiceImpl implements NotificationService {
	
	@Autowired
	private NotificationMapper notificationMapper;
	
	@Autowired
    private FirebaseMessaging firebaseMessaging;
	
	private final Map<Integer, SseEmitter> emitters = new ConcurrentHashMap<>();
	
	@Override
	public SseEmitter subscribe(int userId) {
		// 타임아웃 1h
		SseEmitter emitter = new SseEmitter(3600000L);
		emitters.put(userId, emitter);
		
		emitter.onCompletion(() -> emitters.remove(userId));
		emitter.onTimeout(() -> emitters.remove(userId));
		emitter.onError((e) -> emitters.remove(userId));
		try {
            emitter.send(SseEmitter.event().name("connect").data("connected!"));
        } catch (IOException e) {
            emitters.remove(userId);
        }
        return emitter;
	}
	
	@Override
	public void sendToUser(int userId, NotificationVO noti) {
		SseEmitter emitter = emitters.get(userId);
		if(emitter != null) {
			try {
				emitter.send(SseEmitter.event()
						.name("notification")
						.data(noti)
						);
			} catch(IOException e) {
				emitters.remove(userId);
			}
		}
	}
	
	@Override
	@Transactional
	public void createNotification(int userId, String message, String targetType, int targetId) {
	    NotificationVO noti = new NotificationVO();
	    noti.setUserId(userId); // 💡 여기서 userid(소문자)로 들어가야 Jwt랑 맞습니다.
	    noti.setMessage(message);
	    noti.setTargetType(targetType);
	    noti.setTargetId(targetId);
	    notificationMapper.insertNotification(noti);

	    //  [웹 실시간 SSE] 브라우저 켜져 있으면 바로 팝업
	    sendToUser(userId, noti);

	    try {
            List<String> tokens = notificationMapper.getFcmTokenByUserId(userId);
            System.out.println("📢 [FCM 체크] 유저 " + userId + "의 토큰 개수: " + (tokens != null ? tokens.size() : 0));
            
            if (tokens != null && !tokens.isEmpty()) {
                for (String token : tokens) {
                	System.out.println("🚀 [FCM 발송 시도] 토큰: " + token);
                	
                    // 💡 여기서 직접 Message 객체를 만듭니다. (sendFcmPush 대신!)
                    Message fcmMessage = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                            .setTitle("끼리끼리 알림")
                            .setBody(message)
                            .build())
                        .build();
                    
                    firebaseMessaging.send(fcmMessage); // 🥊 이게 진짜 전송 명령입니다.
                }
            }
        } catch (Exception e) {
            System.out.println("❌ FCM 발송 중 에러: " + e.getMessage());
        }
    }
	
	
	@Override
	public List<NotificationVO> getNotificationList(int userId) {
		return notificationMapper.selectNotiList(userId);
	}
	
	@Override
	public void markAsRead(int notiId) {
		notificationMapper.updateReadStatus(notiId);
	}
	
	@Override
    @Transactional
    public void markAllAsRead(int userId) {
        notificationMapper.updateAllReadStatus(userId);
    }
	
	@Override
	public boolean hasUnreadNoti(int userId) {
		int count = notificationMapper.unreadNoti(userId);	
		
		return count > 0;
	}

}



