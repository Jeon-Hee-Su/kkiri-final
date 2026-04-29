package com.kkiri.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVO {
	private int notiId;      // NOTI_ID
    private int userId;      // USER_ID
    private String message;   // MESSAGE
    private String targetType;// TARGET_TYPE (예: SETTLE, GROUP)
    private int targetId;    // TARGET_ID (해당 글번호 등)
    private String isRead;    // IS_READ ('N' 또는 'Y')
    private String createdAt; // CREATED_AT (String 또는 LocalDateTime)
    
 // 편리성을 위한 커스텀 생성자
    public NotificationVO(int userId, String message, String targetType, int targetId) {
        this.userId = userId;
        this.message = message;
        this.targetType = targetType;
        this.targetId = targetId;
    }
}