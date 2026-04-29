// AccountMapper.java
package com.kkiri.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountMapper {
    String findPaymentPasswordByGroupId(int groupId);
    
    String findPaymentPasswordByUserId(int userId);
}