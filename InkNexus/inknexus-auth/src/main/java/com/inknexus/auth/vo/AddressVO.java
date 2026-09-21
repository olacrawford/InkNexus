package com.inknexus.auth.vo;

import lombok.Data;

/**
 * 收货地址响应体：地址簿查询/新增/修改接口的返回载荷。
 * <p>字段与 {@code t_user_address} 一一对应，userId 同时回传以便前端确认归属。
 */
@Data
public class AddressVO {

    private Long id;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;

    // 是否默认地址：0 否 1 是
    private Integer isDefault;

}