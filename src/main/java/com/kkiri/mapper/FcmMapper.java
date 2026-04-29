package com.kkiri.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kkiri.model.vo.NotificationVO;

@Mapper
public interface FcmMapper {
	void saveOrUpdateToken(@Param("userId") int userId, @Param("fcmToken") String fcmToken, @Param("uuid") String uuid);
	
	List<String> findAllTokensByUserId(int userId);
	
	void deleteToken(String fcmToken);
}


