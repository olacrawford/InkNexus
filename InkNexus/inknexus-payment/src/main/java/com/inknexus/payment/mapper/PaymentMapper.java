package com.inknexus.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付单表访问层：无自定义 SQL，直接复用 MyBatis-Plus 通用 CRUD。
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
