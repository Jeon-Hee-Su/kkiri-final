package com.kkiri.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kkiri.model.vo.NotificationVO;

@Mapper
public interface NotificationMapper{
	// 알림 내역 저장
    void insertNotification(@Param("noti") NotificationVO noti);
    
 // [수정됨] 특정 유저의 알림 목록 조회 (최신순 20개)
    List<NotificationVO> selectNotiList(@Param("userId") int userId);

    // [수정됨] 개별 알림 읽음 처리 (isRead = 'Y')
    void updateReadStatus(@Param("notiId") int notiId);

    // 전체 알림 읽음 처리
    void updateAllReadStatus(@Param("userId") int userId);
    
    List<String> getFcmTokenByUserId(@Param("userId") int userId);
    
    int unreadNoti(@Param("userId") int userId);
}