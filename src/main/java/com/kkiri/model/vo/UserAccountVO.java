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
public class UserAccountVO {
    private int accountId;       // ACCOUNT_ID
    private int userId;          // USER_ID (외래키)
    private String bankCode;     // BANK_CODE
    private String accountNumber; // ACCOUNT_NUMBER
    private long balance;         // BALANCE (기본 1,000,000)
    private String isPrimary;     // IS_PRIMARY
    private String customerUid;   // CUSTOMER_UID (추가할 컬럼! PortOne 빌링키)
 // private Timestamp createdAt;  // CREATED_AT
 // private String isDeleted;     // IS_DELETED
    private String status;		  // STATUS
    private String bankName;
    private BanksVO bankVO;
    private String paymentPassword; // PAYMENT_PASSWORD (2차 비밀번호, BCrypt 암호화)
   
}