package com.inknexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收货地址实体，对应表 {@code t_user_address}。
 * <p>核心不变量：同一用户最多一条 {@code isDefault=1} 的地址，由 AddressServiceImpl 维护。
 */
@Data
@TableName("t_user_address")
public class UserAddress {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 归属用户id；所有查询/修改都必须带上该条件，防止越权操作他人地址
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;

    // 是否默认地址：0 否 1 是
    private Integer isDefault;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}