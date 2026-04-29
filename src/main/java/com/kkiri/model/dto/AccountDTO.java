package com.kkiri.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AccountDTO {

	// ──────────────────────────────────────────
	// 요청(Request) DTOs
	// ──────────────────────────────────────────

	/**
	 * 개인 계좌 등록 요청 POST /api/account/register { "bankCode": "088",
	 * "paymentPassword": "123456" }
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RegisterRequest {
		private String bankCode; // 은행 코드
		private String paymentPassword; // 2차 비밀번호 (평문 6자리, 서비스에서 BCrypt 암호화)
	}

	/**
	 * 계좌번호 미리보기 생성 요청 POST /api/account/create { "bankCode": "088" }
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PreviewRequest {
		private String bankCode;
	}

	// ──────────────────────────────────────────
	// 응답(Response) DTOs
	// ──────────────────────────────────────────

	/**
	 * 계좌 목록 조회 응답 (1건) GET /api/account/list
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AccountInfo {
		private int accountId;
		private String bankName;
		private String accountNumber;
		private String isDefault; // 'Y' / 'N'
		private long balance;
	}

	/**
	 * 주계좌 단건 조회 응답 GET /api/account/my-account
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MyAccountResponse {
		private boolean hasAccount;
		private AccountInfo account; // 주계좌 1건 (hasAccount=false 이면 null)
	}

	/**
	 * 계좌번호 미리보기 응답 POST /api/account/create
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PreviewResponse {
		private String accountNumber;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class VerifyPasswordRequest {
		private int groupId;
		private String paymentPassword;
	}

	// 비밀번호 검증 응답
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class VerifyPasswordResponse {
		private boolean success;
		private String message;
	}
}