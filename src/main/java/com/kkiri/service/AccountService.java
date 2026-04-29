package com.kkiri.service;

public interface AccountService {
	
	//개인계좌 개설 (2차 비밀번호 포함)
	String createUserAccount(int userId, String bankCode, String paymentPassword);
	//모임계좌 개설
	String createGroupAccount(int groupId, String bankCode, String pin);
	//은행 코드로 계좌개설 
	String generateAccountNumber(String bankCode);
	//그룹 2차 비밀번호 확인
	boolean checkPaymentPassword(int groupId, String inputPassword);
}