package com.inknexus.book.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel限流规则配置类
 * 代码方式定义限流规则，针对listBooks资源做QPS限流；
 * 也可以不在代码写，直接在Sentinel控制台网页配置规则
 */
@Configuration // 标识为Spring配置类，项目启动会加载该类
public class SentinelConfig {

    /**
     * @PostConstruct：对象创建完成、依赖注入完毕之后，自动执行这个initFlowRules初始化方法
     * 作用：项目启动成功后立刻加载Sentinel限流规则
     */
    @PostConstruct
    public void initFlowRules() {
        // 存放多条限流规则的集合，可以配置多个资源的限流
        List<FlowRule> rules = new ArrayList<>();

        // 按查询接口拆分规则，正常访问不会误伤，压测时才触发限流
        rules.add(createFlowRule("listBooks", 50));
        rules.add(createFlowRule("pageBooks", 80));
        rules.add(createFlowRule("getBookById", 120));
        rules.add(createFlowRule("listCategories", 80));

        // 将规则加载到Sentinel管理器，规则正式生效
        FlowRuleManager.loadRules(rules);
    }

    private FlowRule createFlowRule(String resource, int count) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(count);
        return rule;
    }
}
