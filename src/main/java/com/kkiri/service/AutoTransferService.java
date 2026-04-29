package com.kkiri.service;

//자동이체 서비스

public interface AutoTransferService {
	//자동이체 실행 -> 매일매일 정해진 시간에 스케쥴러 실행
	void executeAutoTransfers();
}
