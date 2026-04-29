package com.kkiri.service;

import java.util.List;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.kkiri.model.vo.NotificationVO;

public interface NotificationService {
	// SSE구독 연결
	SseEmitter subscribe(int userId);
	
	// 사용자에게 실시간 알림
	void sendToUser(int usereId, NotificationVO noti);
	
	// 알림 목록 조회
	List<NotificationVO> getNotificationList(int userId);

	// 전체 읽음
	void markAllAsRead(int userId);
	
	// 알림 읽음 처리
	void markAsRead(int notiId);

	void createNotification(int userId, String message, String targetType, int targetId);
	
	boolean hasUnreadNoti(int userId);

	// 접속중인 모든 사용자에게 공지사항 => 보류
	// void sendToAll(NotificationVO noti);
}
