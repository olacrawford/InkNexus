package com.inknexus.ai.tool;

import com.inknexus.ai.feign.OrderFeignClient;
import com.inknexus.ai.feign.dto.OrderDetailSnapshot;
import com.inknexus.ai.feign.dto.OrderItemSnapshot;
import com.inknexus.ai.feign.dto.OrderSnapshot;
import com.inknexus.ai.support.ResultUtils;
import com.inknexus.common.context.UserContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * 订单查询工具：暴露给 AI 模型使用的 @Tool，只读调用 order 服务，不写入任何订单数据。
 * <p>用户绑定是本类的安全核心：@Tool 参数里没有 userId，模型无法指定“查谁的订单”；
 * 当前用户身份由 ChatServiceImpl 在每次请求时经 toolContext 传入（来源于网关注入的 X-User-Id），
 * 工具执行时写入 ThreadLocal，使 FeignAuthConfig 能把 X-User-Id 透传给下游，
 * order 服务再按该 ID 校验订单归属，因此对话中无论怎么引导模型，都只能查到当前登录用户自己的订单。
 * <p>用 toolContext 而不是直接读 ThreadLocal 的原因：流式对话下工具在模型调用的回调线程执行，
 * 请求线程的 ThreadLocal 在那里不可见，显式传值不挑执行线程。
 * <p>工具内不捕获业务异常：身份缺失、订单不存在、下游服务失败等直接抛出，
 * 由 ToolExecutionExceptionProcessor 统一转成模型可读的失败说明继续对话。
 */
@Component
@RequiredArgsConstructor
public class QueryOrderTool {

    private final OrderFeignClient orderFeignClient;

    /** 查当前用户最近订单列表。 */
    @Tool(description = "查询当前登录用户的最近订单列表（订单号/金额/状态/下单时间）")
    public List<OrderBrief> queryMyOrders(@ToolParam(description = "返回条数，不传默认5，最大10", required = false) Integer topN,
                                          ToolContext toolContext) {
        return withCurrentUser(toolContext, userId -> {
            List<OrderSnapshot> list = ResultUtils.data(orderFeignClient.listOrders());
            if (list == null) {
                return List.of();
            }
            int limit = topN == null ? 5 : Math.min(topN, 10);
            return list.stream()
                    .limit(limit)
                    .map(order -> new OrderBrief(order.getOrderNo(), order.getTotalAmount(),
                            statusText(order.getStatus()), String.valueOf(order.getCreateTime())))
                    .toList();
        });
    }

    /** 查某笔订单详情（商品明细 + 收货信息 + 状态）。 */
    @Tool(description = "查询当前登录用户某笔订单的详细进度（商品明细/收货信息/状态）")
    public OrderDetailBrief queryOrderDetail(@ToolParam(description = "订单ID") Long orderId,
                                             ToolContext toolContext) {
        return withCurrentUser(toolContext, userId -> {
            if (orderId == null) {
                throw new IllegalArgumentException("订单ID不能为空");
            }
            OrderDetailSnapshot detail = ResultUtils.data(orderFeignClient.getOrderDetail(orderId));
            if (detail == null) {
                throw new IllegalStateException("订单不存在或不属于当前用户");
            }
            List<OrderItemSnapshot> items = detail.getItems();
            List<OrderItemBrief> itemBriefs = (items == null ? List.<OrderItemSnapshot>of() : items).stream()
                    .map(item -> new OrderItemBrief(item.getBookTitle(), item.getBookPrice(),
                            item.getQuantity(), item.getSubtotal()))
                    .toList();
            return new OrderDetailBrief(detail.getOrderNo(), detail.getTotalAmount(),
                    statusText(detail.getStatus()), detail.getReceiverName(),
                    detail.getReceiverAddress(), itemBriefs);
        });
    }

    /**
     * 从 toolContext 取出当前用户 ID，写入 common 的 UserContext 后执行查询
     * （Feign 拦截器据此注入 X-User-Id），执行完无论成败都清理，防止线程池复用串号。
     */
    private <T> T withCurrentUser(ToolContext toolContext, Function<Long, T> query) {
        Long userId = requireCurrentUser(toolContext);
        UserContext.setUserId(userId);
        try {
            return query.apply(userId);
        } finally {
            UserContext.clear();
        }
    }

    private Long requireCurrentUser(ToolContext toolContext) {
        Object userId = toolContext.getContext().get("userId");
        if (userId == null) {
            throw new IllegalStateException("未识别到当前登录用户");
        }
        try {
            return Long.valueOf(userId.toString());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("当前用户身份不合法");
        }
    }

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

    /** 订单摘要（工具返回值，Spring AI 序列化为 JSON 提供给模型）。 */
    record OrderBrief(String orderNo, BigDecimal totalAmount, String status, String createTime) {
    }

    /** 订单商品明细（工具返回值）。 */
    record OrderItemBrief(String bookTitle, BigDecimal bookPrice, Integer quantity, BigDecimal subtotal) {
    }

    /** 订单详情（工具返回值）。 */
    record OrderDetailBrief(String orderNo, BigDecimal totalAmount, String status,
                            String receiverName, String receiverAddress, List<OrderItemBrief> items) {
    }
}
