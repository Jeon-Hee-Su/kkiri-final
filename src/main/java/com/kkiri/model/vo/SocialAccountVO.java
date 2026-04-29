package com.kkiri.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialAccountVO {
    private int socialId;
    private int userId;
    private String provider;
    private String providerId;
    private Timestamp createdAt;
}