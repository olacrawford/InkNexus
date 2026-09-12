package com.inknexus.ai.tool;

import com.inknexus.ai.feign.OrderFeignClient;
import com.inknexus.ai.feign.dto.OrderDetailSnapshot;
import com.inknexus.ai.feign.dto.OrderItemSnapshot;
import com.inknexus.ai.feign.dto.OrderSnapshot;
import com.inknexus.ai.support.ResultUtils;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 订单查询工具：暴露给 AI 模型使用的 @Tool，只读调用 order 服务，不写入任何订单数据。 */
@Component
@RequiredArgsConstructor
public class QueryOrderTool {

    private final OrderFeignClient orderFeignClient;

    /** 查当前用户最近订单列表。userId 由 Feign 拦截器自动带上 X-User-Id，只回自己名下订单。 */
    @Tool("查询当前登录用户的最近订单列表，返回前 N 条（订单号/金额/状态/下单时间）")
    public String queryMyOrders(@P("topN") Integer topN) {
        List<OrderSnapshot> list = ResultUtils.data(orderFeignClient.listOrders());
        if (list == null || list.isEmpty()) {
            return "您还没有订单";
        }
        int limit = topN == null ? 5 : Math.min(topN, 10);
        return list.stream()
                .limit(limit)
                .map(order -> String.format("订单号:%s 金额:¥%s 状态:%s 下单:%s",
                        order.getOrderNo(), order.getTotalAmount(),
                        statusText(order.getStatus()), order.getCreateTime()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("您还没有订单");
    }

    /** 查某笔订单详情（商品明细 + 收货信息 + 状态）。订单服务会校验归属，非本人订单返回空。 */
    @Tool("查询当前登录用户某笔订单的详细进度（商品明细/收货信息/状态）")
    public String queryOrderDetail(@P("orderId") Long orderId) {
        if (orderId == null) {
            return "请提供订单ID";
        }
        OrderDetailSnapshot detail = ResultUtils.data(orderFeignClient.getOrderDetail(orderId));
        if (detail == null) {
            return "没有找到该订单，或该订单不属于您";
        }
        List<OrderItemSnapshot> items = detail.getItems();
        // 把商品明细拼成可读文本
        String itemText = (items == null || items.isEmpty())
                ? "无商品明细"
                : items.stream()
                        .map(item -> String.format("《%s》 ¥%s x%d 小计¥%s",
                                item.getBookTitle(), item.getBookPrice(),
                                item.getQuantity(), item.getSubtotal()))
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("无商品明细");
        return String.format("订单号:%s 金额:¥%s 状态:%s 收货:%s %s 商品明细:\n%s",
                detail.getOrderNo(), detail.getTotalAmount(), statusText(detail.getStatus()),
                detail.getReceiverName(), detail.getReceiverAddress(), itemText);
    }

    /** 把订单状态数字转成中文文本，方便模型直接读给用户。 */
    private String statusText(Integer status) {
        // 订单状态：0待支付 1已支付 2已取消 3已完成
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已取消";
            case 3 -> "已完成";
            default -> "未知";
        };
    }
}