package com.inknexus.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增/修改收货地址的请求体（POST /auth/addresses、PUT /auth/addresses/{id} 共用）。
 * <p>userId 不在请求体中传递，由服务端从网关注入的 {@code X-User-Id} 头解析。
 */
@Data
public class AddressRequest {

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "收货电话不能为空")
    private String receiverPhone;

    // 省市区可选，仅用于展示分层；详细地址必填
    private String province;
    private String city;
    private String district;

    @NotBlank(message = "详细地址不能为空")
    private String detailAddress;

    // 是否设为默认地址：null 表示修改时保持原状态不变；true 触发"清空其他默认"逻辑
    private Boolean isDefault;

}