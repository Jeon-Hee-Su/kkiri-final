package com.kkiri.model.vo;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class RefreshTokenVO {

	private Long tokenId;
    private Long userId;
    private String tokenValue;
    private LocalDateTime expiryDate;
    private LocalDateTime createdAt;
}
