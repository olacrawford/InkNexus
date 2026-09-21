package com.inknexus.book.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流规则配置：资源名与 Service 层 {@code @SentinelResource(value = "...")} 一一对应，
 * QPS 超阈值时请求改走注解声明的 blockHandler 抛 BusinessException(429)；
 * 保护四个高频读接口（列表/分页/详情/分类），即读多写少场景下最易打垮 DB 的路径。
 */
@Configuration
public class SentinelConfig {

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 按查询接口拆分规则，正常访问不会误伤，压测时才触发限流
        rules.add(createFlowRule("listBooks", 50));
        rules.add(createFlowRule("pageBooks", 80));
        rules.add(createFlowRule("getBookById", 120));
        rules.add(createFlowRule("listCategories", 80));

        FlowRuleManager.loadRules(rules);
    }

    /**
     * 组装单条 QPS 流控规则：统计窗口内每秒请求数超过 count 即拒绝（默认「直接拒绝」策略，快速失败）
     */
    private FlowRule createFlowRule(String resource, int count) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(count);
        return rule;
    }
}
