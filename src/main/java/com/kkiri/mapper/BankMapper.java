package com.kkiri.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BankMapper {
	String findBankName(@Param("code") String code);
}
