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
public class UserPrivacyVO {
    private int userId;
    private String ci;
    private String di;
    private String name;
    private String phoneNumber;
    private String birthDate;
    private Timestamp updatedAt;
}