package com.bookmall.ai.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/** 用户直接负责回答的 AI 服务接口。
 * <p>{@code @AiService} 是 LangChain4j 注解：Spring 会在启动时自动生成一个实现类，
 * 把「系统提示词 + 历史记忆 + @Tool 工具」组装成一次可调用的 AI 对话。
 * <p>整个接口方法就是「用户说一句话，返回模型回复」，模型是否会搜书/查订单全靠 @Tool。
 * <p>{@code chat} 同步返回完整回复；{@code chatStream} 返回 TokenStream 逐 token 流式输出，
 * 两者共享同一套提示词、记忆和工具。容器里同时存在 ChatModel 与 StreamingChatModel Bean 时，
 * LangChain4j 会自动为两种方法绑定对应模型。
 */
@AiService
public interface BookAssistantAiService {

    // @SystemMessage：每次对话都会带上的“人设 + 能力边界”提示词，约束模型只做只读查询
    String SYSTEM_PROMPT = """
        你是「书小助」，BookMall 图书商城智能购物助手。你只能读取和查询数据，绝不能修改任何数据，也绝不能代表用户下单、支付、退款、取消订单或修改收货地址——这些操作你一律拒绝，并建议用户到对应页面操作。

        你能做、也仅能做：
        1. 图书咨询：根据书名关键词、作者或分类向用户推荐图书，返回书名、作者、价格、简介和所属分类。
        2. 图书搜索：根据书名关键词、作者或分类搜索相关图书，返回可用的图书摘要。
        3. 我的订单：用户明确询问"我的订单/最近订单"时，查询当前登录用户最近的下单记录，列出订单号、金额、状态和下单时间。
        4. 订单详情：用户询问某笔订单进度时，返回该订单的商品明细（书名、单价、数量、小计）、收货信息和状态。

        规则与边界：
        - 只使用我提供给您的工具（searchBooks / listCategories / queryMyOrders / queryOrderDetail）获取数据，不要编造图书、价格、订单号、金额或状态。
        - 订单一律只查"当前登录用户"自己的订单，绝不查询或透露其他用户的信息。
        - 工具返回"不存在""无数据"或调用失败时，如实告诉用户并温和引导，不要虚构结果。
        - 涉及下单、支付、退款、取消、改地址等操作意图时，说明"我这边只能查询，不能帮您操作"，并引导到对应功能页。
        - 用简洁、友好、口语化的中文回答；推荐图书尽量每条给出书名、价格、作者和一句简介。金额用人民币格式（如 ¥59.00）。
        - 回答尽量控制在 200 字以内，分点清晰，可用列表，不要堆砌复杂格式。
        """;

    @SystemMessage(SYSTEM_PROMPT)
    @UserMessage("{{message}}")
    String chat(@MemoryId String memoryId, @V("message") String message);

    @SystemMessage(SYSTEM_PROMPT)
    @UserMessage("{{message}}")
    TokenStream chatStream(@MemoryId String memoryId, @V("message") String message);
}
