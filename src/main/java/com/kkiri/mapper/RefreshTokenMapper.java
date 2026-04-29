package com.kkiri.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.kkiri.model.vo.RefreshTokenVO;

@Mapper
public interface RefreshTokenMapper {
    
    // 토큰으로 정보 조회
    RefreshTokenVO findByToken(@Param("tokenValue") String tokenValue);
    
    // 토큰 저장 (Upsert 로직 권장)
    void saveRefreshToken(@Param("userId") Long userId, 
                          @Param("tokenValue") String tokenValue, 
                          @Param("expiryDate") LocalDateTime expiryDate);
    
    // 유저 ID로 토큰 삭제 (로그아웃)
    void deleteByUserId(@Param("userId") Long userId);

    // [추가] 토큰 유효성 여부 확인 (에러 해결 포인트)
    boolean existsByToken(@Param("tokenValue") String tokenValue);
    
    String findEmailByUserId(@Param("userId") Long userId);
    
    int updateRefreshToken(@Param("userId") Long userId,
            @Param("tokenValue") String tokenValue,
            @Param("expiryDate") LocalDateTime expiryDate);
}