package com.kkiri.service;

public interface FcmService {
	// 프론트에서 넘어온 토큰 저장
    void saveToken(int userId, String fcmToken, String uuid);

    // 유저의 모든 기기에 푸시 발송
    void sendPushToUser(int userId, String title, String body);
	
    // 토큰을 삭제할경우
    void deleteToken(String fcmToken);
    
    
}
