# ai-assistant 微服务设计文档（阶段·逐文件施工版）

> 项目：BookMall（Spring Boot 3.2.5 + Spring Cloud 2023.0.2 + Spring Cloud Alibaba 2023.0.1.0 + Nacos + OpenFeign + Redis）
> 服务：`ai-assistant`｜模块：`bookmall-ai`｜包根：`com.bookmall.ai`
> 定位：独立部署、只读不写的 AI 问答服务；用 LangChain4j 接入阿里云通义千问（DashScope）；会话记忆存 Redis。
> 端口：`8071`

---

## 0. 一次看懂：全模块文件清单（按阶段）

| 阶段 | 要写的文件 | 作用 |
| --- | --- | --- |
| 一 骨架 | `BookMall/pom.xml`（改） | 注册模块、锁定 LangChain4j BOM |
| 一 骨架 | `bookmall-ai/pom.xml` | 模块依赖 |
| 一 骨架 | `bookmall-ai/src/main/resources/application.yml` | 端口/服务名/Nacos |
| 一 骨架 | `nacos-config/ai-assistant.yaml` | Redis/Feign/DashScope 配置 |
| 一 骨架 | `nacos-config/publish.sh`（改） | 发布到 Nacos 的服务列表 |
| 一 骨架 | `com/bookmall/ai/AiAssistantApplication.java` | 启动类 |
| 二 模型 | `config/AiModelConfig.java` | 构建 DashScope 模型 Bean |
| 二 模型 | `ai/BookAssistantAiService.java` | `@AiService` + SystemMessage |
| 三 只读 | `context/UserContextHolder.java` | 保存网关透传的 userId |
| 三 只读 | `context/UserContextInterceptor.java` | 拦截请求写/清 ThreadLocal |
| 三 只读 | `config/WebMvcConfig.java` | 注册拦截器 |
| 三 只读 | `config/FeignAuthConfig.java` | Feign 透传 `X-User-Id` |
| 三 只读 | `feign/dto/BookSnapshot.java` 等 5 个 DTO | book/order 返回镜像 |
| 三 只读 | `feign/BookFeignClient.java` | 调 book 查询接口 |
| 三 只读 | `feign/OrderFeignClient.java` | 调 order 查询接口 |
| 三 只读 | `support/ResultUtils.java` | 解包 `Result` / `PageResult` |
| 三 只读 | `tool/QueryBookTool.java` | 搜书工具 |
| 三 只读 | `tool/QueryOrderTool.java` | 查订单工具 |
| 四 编排 | `config/ChatMemoryConfig.java` | 会话记忆 Bean |
| 四 编排 | `support/RedisChatMemoryStore.java` | 记忆落地 Redis |
| 四 编排 | `dto/ChatRequest.java` | 请求体 |
| 四 编排 | `dto/ChatResponse.java` | 响应体 |
| 四 编排 | `service/ChatService.java` | 接口 |
| 四 编排 | `service/impl/ChatServiceImpl.java` | 编排 |
| 四 编排 | `controller/AiAssistantController.java` | 对外 REST |
| 五 网关 | `bookmall-gateway/src/main/resources/application.yml`（改） | 加 `/api/ai/**` 路由 |
| 五 网关 | 环境变量 / 启动脚本 | `DASHSCOPE_API_KEY`、运行命令 |
| 六 验证 | `src/test/.../ResultUtilsTest`、`ChatServiceImplTest` | 联调 + 文档同步 |

---

# 阶段一：工程骨架接入

**目标**：模块能被父 POM 编译、注册到 Nacos、`/ai/hello` 能通。

## 1.1 改 `BookMall/pom.xml`

在 `<modules>` 中加一行：

```xml
<module>bookmall-ai</module>
```

在 `<properties>` 中加版本：

```xml
<langchain4j.version>1.1.3</langchain4j.version>
```

在 `<dependencyManagement><dependencies>` 中加 BOM（放在已有 import 之后）：

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-bom</artifactId>
    <version>${langchain4j.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

> BOM 一旦引入，子模块里 `langchain4j` 系列都不用写版本号。

## 1.2 新建 `BookMall/bookmall-ai/pom.xml`

完整文件内容：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.bookmall</groupId>
        <artifactId>bookmall</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>bookmall-ai</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.bookmall</groupId>
            <artifactId>bookmall-common</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>

        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>

        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
        </dependency>

        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

## 1.3 新建 `bookmall-ai/src/main/resources/application.yml`

完整文件内容：

```yaml
server:
  port: 8071

spring:
  application:
    name: ai-assistant
  config:
    import:
      - optional:nacos:ai-assistant.yaml
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
      discovery:
        server-addr: localhost:8848
        namespace: public
        group: DEFAULT_GROUP
```

> 只有端口/服务名/Nacos 放本地，DB、Redis、DashScope、Feign 配置放 Nacos（与现有模块一致）。

## 1.4 新建 `nacos-config/ai-assistant.yaml`

完整文件内容：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 3000
            read-timeout: 8000

dashscope:
  api-key: ${DASHSCOPE_API_KEY:}
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model: qwen-plus
  temperature: 0.7
  max-tokens: 1024
  timeout: 30s

ai-assistant:
  chat:
    memory-ttl-hours: 2
    tool-result-limit: 5
```

## 1.5 改 `nacos-config/publish.sh`

把循环列表从：

```bash
for svc in auth book cart stock order payment gateway; do
```

改成：

```bash
for svc in auth book cart stock order payment gateway ai-assistant; do
```

发布：`cd nacos-config && bash publish.sh`

## 1.6 新建 `com/bookmall/ai/AiAssistantApplication.java`

完整文件内容：

```java
package com.bookmall.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.bookmall.ai", "com.bookmall.common"})
@EnableFeignClients
public class AiAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiAssistantApplication.class, args);
    }
}
```

**这一步怎么验证**：`mvn -f BookMall/pom.xml -q clean package` 编译通过；启动后 Nacos 列表出现 `ai-assistant`；`curl http://localhost:8071/ai/hello` 仍会 404（Controller 在阶段四），可先只确认注册成功。

---

# 阶段二：DashScope 模型接入 + 声明式 AI 能力

**目标**：跑通「消息 → 千问 → 回复」。

## 2.1 新建 `com/bookmall/ai/config/AiModelConfig.java`

完整文件内容：

```java
package com.bookmall.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "dashscope")
@Data
public class AiModelConfig {

    private String apiKey;
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String model = "qwen-plus";
    private Double temperature = 0.7;
    private Integer maxTokens = 1024;
    private Duration timeout = Duration.ofSeconds(30);

    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .build();
    }
}
```

> `api-key` 用的是 `${DASHSCOPE_API_KEY:}`，运行时 `export DASHSCOPE_API_KEY=sk-xxx`，代码里不出现明文。

## 2.2 新建 `com/bookmall/ai/ai/BookAssistantAiService.java`

完整文件内容（SystemMessage 是下面这段完整文案，直接用）：

```java
package com.bookmall.ai.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface BookAssistantAiService {

    @SystemMessage("""
        你是「书小助」，BookMall 图书商城智能购物助手。你只能读取和查询数据，绝不能修改任何数据，也绝不能代表用户下单、支付、退款、取消订单或修改收货地址——这些操作你一律拒绝，并建议用户到对应页面操作。

        你能做、也仅能做：
        1. 图书咨询：根据书名关键词、作者或分类向用户推荐图书，返回书名、作者、价格、简介和所属分类。
        2. 图书详情：用户给出具体书名或图书ID时，返回该图书的价格、简介、封面、状态（在售/下架）。
        3. 我的订单：用户明确询问"我的订单/最近订单"时，查询当前登录用户最近的下单记录，列出订单号、金额、状态和下单时间。
        4. 订单详情：用户询问某笔订单进度时，返回该订单的商品明细（书名、单价、数量、小计）、收货信息和状态。

        规则与边界：
        - 只使用我提供给您的工具（searchBooks / getBookById / listCategories / queryMyOrders / queryOrderDetail）获取数据，不要编造图书、价格、订单号、金额或状态。
        - 订单一律只查"当前登录用户"自己的订单，绝不查询或透露其他用户的信息。
        - 工具返回"不存在""无数据"或调用失败时，如实告诉用户并温和引导，不要虚构结果。
        - 涉及下单、支付、退款、取消、改地址等操作意图时，说明"我这边只能查询，不能帮您操作"，并引导到对应功能页。
        - 用简洁、友好、口语化的中文回答；推荐图书尽量每条给出书名、价格、作者和一句简介。金额用人民币格式（如 ¥59.00）。
        - 回答尽量控制在 200 字以内，分点清晰，可用列表，不要堆砌复杂格式。
        """)
    @UserMessage("{{message}}")
    String chat(@MemoryId String memoryId, @V("message") String message);
}
```

> `@MemoryId` 是会话键，用来区分用户/会话，后续记忆存取按它来。**这一步先只测文案回复，暂不接工具。**

---

# 阶段三：只读数据接入（Feign + 工具）

**目标**：模型能搜书、查订单；只查不改。

## 3.1 新建 `com/bookmall/ai/context/UserContextHolder.java`

完整文件内容：

```java
package com.bookmall.ai.context;

public class UserContextHolder {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
```

## 3.2 新建 `com/bookmall/ai/context/UserContextInterceptor.java`

完整文件内容：

```java
package com.bookmall.ai.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uid = request.getHeader("X-User-Id");
        if (uid != null) {
            UserContextHolder.setUserId(Long.valueOf(uid));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}
```

## 3.3 新建 `com/bookmall/ai/config/WebMvcConfig.java`

完整文件内容：

```java
package com.bookmall.ai.config;

import com.bookmall.ai.context.UserContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserContextInterceptor());
    }
}
```

## 3.4 新建 `com/bookmall/ai/config/FeignAuthConfig.java`

完整文件内容（把 ThreadLocal 里的 userId 写进 Feign 请求头）：

```java
package com.bookmall.ai.config;

import com.bookmall.ai.context.UserContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor userContextFeignInterceptor() {
        return (RequestTemplate template) -> {
            Long userId = UserContextHolder.getUserId();
            if (userId != null) {
                template.header("X-User-Id", userId.toString());
            }
        };
    }
}
```

> 关键点：order 的列表/详情接口都要求 `@RequestHeader("X-User-Id")`。这里从网关透传的线程身份取值，**绝不把 userId 交给模型或工具方法参数**。

## 3.5 新建 5 个 DTO（`com/bookmall/ai/feign/dto/`）

`BookSnapshot.java`（覆盖 BookVO + BookDetailVO 字段，未返回字段自动为 null）：

```java
package com.bookmall.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BookSnapshot {
    private Long id;
    private String title;
    private String author;
    private BigDecimal price;
    private String coverUrl;
    private Long categoryId;
    private String description;
    private Integer status;
}
```

`CategorySnapshot.java`：

```java
package com.bookmall.ai.feign.dto;

import lombok.Data;

@Data
public class CategorySnapshot {
    private Long id;
    private String name;
    private Integer sort;
}
```

`OrderSnapshot.java`：

```java
package com.bookmall.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderSnapshot {
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
}
```

`OrderItemSnapshot.java`：

```java
package com.bookmall.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemSnapshot {
    private Long bookId;
    private String bookTitle;
    private BigDecimal bookPrice;
    private Integer quantity;
    private BigDecimal subtotal;
}
```

`OrderDetailSnapshot.java`：

```java
package com.bookmall.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDetailSnapshot {
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer status;
    private LocalDateTime expireTime;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private List<OrderItemSnapshot> items;
}
```

> 镜像字段来自 order 的 `OrderDetailVO.OrderItemVO`：`bookId/bookTitle/bookPrice/quantity/subtotal`。

## 3.6 新建 `com/bookmall/ai/feign/BookFeignClient.java`

完整文件内容：

```java
package com.bookmall.ai.feign;

import com.bookmall.ai.feign.dto.BookSnapshot;
import com.bookmall.ai.feign.dto.CategorySnapshot;
import com.bookmall.common.result.PageResult;
import com.bookmall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "book")
public interface BookFeignClient {

    @GetMapping("/books/page")
    Result<PageResult<BookSnapshot>> pageBooks(
            @RequestParam("pageNum") Integer pageNum,
            @RequestParam("pageSize") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId);

    @GetMapping("/books/{id}")
    Result<BookSnapshot> getBookById(@PathVariable("id") Long id);

    @GetMapping("/books/categories")
    Result<List<CategorySnapshot>> listCategories();
}
```

## 3.7 新建 `com/bookmall/ai/feign/OrderFeignClient.java`

完整文件内容：

```java
package com.bookmall.ai.feign;

import com.bookmall.ai.feign.dto.OrderDetailSnapshot;
import com.bookmall.ai.feign.dto.OrderSnapshot;
import com.bookmall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "order")
public interface OrderFeignClient {

    @GetMapping("/orders")
    Result<List<OrderSnapshot>> listOrders();

    @GetMapping("/orders/{id}")
    Result<OrderDetailSnapshot> getOrderDetail(@PathVariable("id") Long id);
}
```

## 3.8 新建 `com/bookmall/ai/support/ResultUtils.java`

解包公共 `Result` / `PageResult`，非 200 抛 `BusinessException`：

```java
package com.bookmall.ai.support;

import com.bookmall.common.constant.ErrorCode;
import com.bookmall.common.exception.BusinessException;
import com.bookmall.common.result.Result;

public class ResultUtils {

    private ResultUtils() {
    }

    public static <T> T data(Result<T> result) {
        if (result == null || result.getCode() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
```

## 3.9 新建 `com/bookmall/ai/tool/QueryBookTool.java`

完整文件内容：

```java
package com.bookmall.ai.tool;

import com.bookmall.ai.feign.BookFeignClient;
import com.bookmall.ai.feign.dto.BookSnapshot;
import com.bookmall.ai.feign.dto.CategorySnapshot;
import com.bookmall.ai.support.ResultUtils;
import com.bookmall.common.result.PageResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class QueryBookTool {

    private final BookFeignClient bookFeignClient;

    @Tool("按书名关键字搜索图书，返回前 N 条图书摘要（书名/作者/价格）")
    public String searchBooks(@P("keyword") String keyword, @P("topN") Integer topN) {
        if (keyword == null || keyword.isBlank()) {
            return "请提供搜索关键词";
        }
        int limit = topN == null ? 5 : Math.min(topN, 10);
        PageResult<BookSnapshot> page = ResultUtils.data(
                bookFeignClient.pageBooks(1, limit, keyword, null));
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return "没有找到相关图书，可换个关键词试试";
        }
        return page.getRecords().stream()
                .limit(limit)
                .map(book -> String.format("《%s》 %s ¥%s",
                        book.getTitle(), book.getAuthor(), book.getPrice()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("没有找到相关图书");
    }

    @Tool("根据图书ID查询图书详情（含简介、状态、价格、封面）")
    public String getBookById(@P("bookId") Long bookId) {
        if (bookId == null) {
            return "请提供图书ID";
        }
        BookSnapshot book = ResultUtils.data(bookFeignClient.getBookById(bookId));
        if (book == null) {
            return "该图书不存在";
        }
        String status = (book.getStatus() != null && book.getStatus() == 1) ? "在售" : "下架";
        return String.format("《%s》 %s ¥%s 状态:%s 简介:%s",
                book.getTitle(), book.getAuthor(), book.getPrice(), status, book.getDescription());
    }

    @Tool("查询所有图书分类（返回分类ID与名称）")
    public String listCategories() {
        List<CategorySnapshot> list = ResultUtils.data(bookFeignClient.listCategories());
        if (list == null || list.isEmpty()) {
            return "暂无分类";
        }
        return list.stream()
                .map(category -> category.getId() + "-" + category.getName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("暂无分类");
    }
}
```

## 3.10 新建 `com/bookmall/ai/tool/QueryOrderTool.java`

完整文件内容（userId 来自 `UserContextHolder`，**方法里没有 userId 参数**，避免模型伪造他人身份）：

```java
package com.bookmall.ai.tool;

import com.bookmall.ai.feign.OrderFeignClient;
import com.bookmall.ai.feign.dto.OrderDetailSnapshot;
import com.bookmall.ai.feign.dto.OrderItemSnapshot;
import com.bookmall.ai.feign.dto.OrderSnapshot;
import com.bookmall.ai.support.ResultUtils;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class QueryOrderTool {

    private final OrderFeignClient orderFeignClient;

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
```

**这一步怎么验证**：写完可在阶段四的临时 Controller 里问「搜《活着》」「查我最近订单」，看 book/order 日志有 GET 回源、且无写接口调用。

---

# 阶段四：会话记忆 + 业务编排 + Controller

**目标**：多轮上下文可用、记忆存 Redis、对外暴露 REST。

## 4.1 Redis 序列化说明

本模块不单独建 `RedisConfig`，直接用 Spring Boot 自动装配的 `StringRedisTemplate`（`spring-boot-starter-data-redis` 已提供）。会话记忆由 `RedisChatMemoryStore` 手动以 JSON 数组落库，控制序列化格式、避免依赖内部消息类的默认序列化。

## 4.2 新建 `com/bookmall/ai/support/RedisChatMemoryStore.java`

把 LangChain4j 的 `ChatMemoryStore` 落到 Redis，key = `chat:memory:{memoryId}`，带 TTL。存储格式为 `[{"type":"USER|AI|SYSTEM","text":"..."}]`：

```java
package com.bookmall.ai.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "chat:memory:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisChatMemoryStore(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.ttl = ttl;
    }

    private String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = redisTemplate.opsForValue().get(key(memoryId));
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> records = objectMapper.readValue(
                    json, objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            List<ChatMessage> messages = new ArrayList<>();
            for (Map<String, Object> record : records) {
                String type = String.valueOf(record.get("type"));
                String text = String.valueOf(record.get("text"));
                switch (type) {
                    case "USER" -> messages.add(UserMessage.from(text));
                    case "AI" -> messages.add(AiMessage.from(text));
                    case "SYSTEM" -> messages.add(SystemMessage.from(text));
                    default -> { /* 忽略未知类型 */ }
                }
            }
            return messages;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        List<Map<String, String>> records = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage m) {
                records.add(Map.of("type", "USER", "text", safeText(m.singleText())));
            } else if (message instanceof AiMessage m) {
                records.add(Map.of("type", "AI", "text", safeText(m.text())));
            } else if (message instanceof SystemMessage m) {
                records.add(Map.of("type", "SYSTEM", "text", safeText(m.text())));
            }
        }
        try {
            String json = objectMapper.writeValueAsString(records);
            redisTemplate.opsForValue().set(key(memoryId), json, ttl);
        } catch (Exception e) {
            throw new RuntimeException("写入 AI 会话记忆失败", e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(key(memoryId));
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
```

## 4.3 新建 `com/bookmall/ai/config/ChatMemoryConfig.java`

```java
package com.bookmall.ai.config;

import com.bookmall.ai.support.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemoryStore redisChatMemoryStore(StringRedisTemplate redisTemplate,
                                                @Value("${ai-assistant.chat.memory-ttl-hours:2}") long ttlHours) {
        return new RedisChatMemoryStore(redisTemplate, Duration.ofHours(ttlHours));
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore store) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .chatMemoryStore(store)
                .maxMessages(20)
                .build();
    }
}
```

## 4.4 新建 `com/bookmall/ai/dto/ChatRequest.java`

```java
package com.bookmall.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    @NotBlank(message = "消息不能为空")
    private String message;

    private String conversationId;
}
```

## 4.5 新建 `com/bookmall/ai/dto/ChatResponse.java`

```java
package com.bookmall.ai.dto;

import lombok.Data;

@Data
public class ChatResponse {
    private String reply;
    private String conversationId;

    public static ChatResponse of(String reply, String conversationId) {
        ChatResponse r = new ChatResponse();
        r.setReply(reply);
        r.setConversationId(conversationId);
        return r;
    }
}
```

## 4.6 新建 `com/bookmall/ai/service/ChatService.java`

```java
package com.bookmall.ai.service;

import com.bookmall.ai.dto.ChatRequest;
import com.bookmall.ai.dto.ChatResponse;

public interface ChatService {
    ChatResponse chat(Long userId, ChatRequest request);
}
```

## 4.7 新建 `com/bookmall/ai/service/impl/ChatServiceImpl.java`

```java
package com.bookmall.ai.service.impl;

import com.bookmall.ai.ai.BookAssistantAiService;
import com.bookmall.ai.dto.ChatRequest;
import com.bookmall.ai.dto.ChatResponse;
import com.bookmall.ai.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final BookAssistantAiService aiService;

    @Override
    public ChatResponse chat(Long userId, ChatRequest request) {
        String conversationId = request.getConversationId() == null || request.getConversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getConversationId();
        // 记忆键 = 用户 + 会话，天然隔离不同用户与不同会话
        String memoryId = userId + ":" + conversationId;
        String reply = aiService.chat(memoryId, request.getMessage());
        return ChatResponse.of(reply, conversationId);
    }
}
```

## 4.8 新建 `com/bookmall/ai/controller/AiAssistantController.java`

```java
package com.bookmall.ai.controller;

import com.bookmall.ai.dto.ChatRequest;
import com.bookmall.ai.dto.ChatResponse;
import com.bookmall.ai.service.ChatService;
import com.bookmall.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final ChatService chatService;

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("ai-assistant is running");
    }

    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestHeader("X-User-Id") Long userId,
                                     @Valid @RequestBody ChatRequest request) {
        return Result.success(chatService.chat(userId, request));
    }
}
```

**这一步怎么验证**：启动后 `curl -X POST http://localhost:8071/ai/chat -H 'Content-Type: application/json' -H 'X-User-Id: 1' -d '{"message":"你好"}'`；再问一次引用上下文的句子，看 Redis 出现 `chat:memory:*`。

---

# 阶段五：网关路由 + 部署配置

**目标**：外部可访问、配置可发布、运行合规。

## 5.1 改 `bookmall-gateway/src/main/resources/application.yml`

在 `spring.cloud.gateway.routes` 列表末尾追加：

```yaml
- id: ai
  uri: lb://ai-assistant
  predicates:
    - Path=/api/ai/**
  filters:
    - StripPrefix=1
```

> `/api/ai/**` 走默认鉴权（非 `isPublic`），会校验 JWT 并注入 `X-User-Id`；`GET /api/ai/hello` 因 `path.endsWith("/hello")` 属公开。

## 5.2 环境变量（不进 Git）

```bash
export DASHSCOPE_API_KEY='sk-xxxx'
```

## 5.3 启动顺序与命令

先启动 auth / book / cart / stock / order / payment / gateway，再：

```bash
mvn -f BookMall/pom.xml -DskipTests install
mvn -f BookMall/pom.xml -pl bookmall-ai spring-boot:run
```

> `spring-boot:run` 不要加 `-am`（否则会一起启动父 POM）。

---

# 阶段六：联调验证 + 文档同步

## 6.1 验证清单（按序执行）

1. `mvn -f BookMall/pom.xml -q clean package` 全量编译通过。
2. 依次启动各服务（含 ai-assistant）。
3. `GET /api/ai/hello`（不带 token）→ 返回健康文本。
4. 带 JWT `POST /api/ai/chat`：问「推荐几本 AI 相关书籍」→ 校验 book 回源。
5. 问「我的订单」→ 校验 order 回源且 `X-User-Id` 来自网关透传。
6. 问「帮我下单」→ 模型拒绝操作为预期。
7. `redis-cli keys 'chat:memory:*'` → 记录按用户/会话隔离、存在 TTL。

## 6.2 文档同步（同一 PR）

- `README.md`：模块/端口表新增 `ai-assistant`。
- `说明文档/`：新增 ai-assistant 模块与部署说明。
- `AGENTS.md`：模块结构与端口更新。
- `sql/`：无改动（本服务只读回源，不直连数据库）。

---

# 附录 A：依赖的现有接口确认表

## A.1 book-service（Nacos 名 `book`，`BookController`）

| 接口 | 入参 | 出参 | 状态 |
| --- | --- | --- | --- |
| `GET /books/page` | `pageNum/pageSize/keyword/categoryId`（可选） | `Result<PageResult<BookVO>>` | 已存在 |
| `GET /books/{id}` | path `id` | `Result<BookDetailVO>` | 已存在 |
| `GET /books/categories` | 无 | `Result<List<CategoryVO>>` | 已存在 |

`BookVO`：`id, title, author, price, coverUrl, categoryId`
`BookDetailVO`：`id, title, author, price, categoryId, coverUrl, description, status`
`GET /books` 为公开路径，Feign 回源无需 `X-User-Id`。

## A.2 order-service（Nacos 名 `order`，`OrderController`）

| 接口 | 入参 | 出参 | 状态 |
| --- | --- | --- | --- |
| `GET /orders` | 请求头 `X-User-Id` | `Result<List<OrderVO>>` | 已存在 |
| `GET /orders/{id}` | 请求头 `X-User-Id` + path `id` | `Result<OrderDetailVO>` | 已存在 |

`OrderVO`：`id, orderNo, userId, totalAmount, status, expireTime`（`createTime` 在实体/SQL 存在但当前 VO 未暴露；如需下单时间请确认是否补到 VO）
`OrderDetailVO`：`id, orderNo, userId, totalAmount, status, expireTime, receiverName, receiverPhone, receiverAddress, items[{bookId, bookTitle, bookPrice, quantity, subtotal}]`
`status` 语义：`0待支付 1已支付 2已取消 3已完成`

> 确认项：两个接口都要求 `@RequestHeader("X-User-Id")`，与 ai-assistant 的 Feign 透传方案匹配，无需改 order-service。ai-assistant 只调这两个只读接口，**不调用** `POST`、`PUT /cancel`、`PUT /paid`、`PUT /complete`。

---

# 附录 B：数据流转（文字串讲）

1. 前端带 `Authorization: Bearer <jwt>` 请求 `POST /api/ai/chat`。
2. Gateway `AuthGlobalFilter` 校验 JWT，解析出 userId 写入 `X-User-Id`，经 `lb://ai-assistant` 路由、`StripPrefix=1` 落到 `/ai/chat`。
3. `AiAssistantController` 读 `X-User-Id` 与 `message`；`UserContextInterceptor` 已把 userId 写入 ThreadLocal。
4. `ChatServiceImpl` 生成 `memoryId = userId:conversationId`，`RedisChatMemoryStore` 载入历史，然后调用 `BookAssistantAiService.chat(memoryId, message)`。
5. LangChain4j 结合 SystemMessage + 历史 + 用户消息，触发 `@Tool`：
   - 搜书 → `QueryBookTool.searchBooks/getBookById` → `BookFeignClient` → `GET /books/page` / `GET /books/{id}`。
   - 分类 → `listCategories` → `GET /books/categories`。
   - 订单 → `QueryOrderTool.queryMyOrders/queryOrderDetail` → `UserContextFeignInterceptor` 注入 `X-User-Id` → `OrderFeignClient` → `GET /orders` / `GET /orders/{id}`。
6. Feign 返回 `Result`/`PageResult`，`ResultUtils.data()` 取 `data`（非 200 抛 `BusinessException`），工具再格式化为面向模型的简洁字符串。
7. 模型汇总生成回复；`ChatServiceImpl` 将本轮对话写回 Redis，返回 `ChatResponse(reply, conversationId)`。
8. Controller 包装 `Result<ChatResponse>` 返回；`afterCompletion` 清理 ThreadLocal。

全程写方向仅一处（会话记忆写 Redis）；book / order / stock 均只读。

---

# 附录 C：实施落地状态

以下设计已在 `BookMall/bookmall-ai` 落地并通过编译与单测，文档与实现一致。

## C.1 已落地文件

- 工程：`bookmall-ai/pom.xml`、`src/main/resources/application.yml`
- 启动：`AiAssistantApplication.java`
- 模型：`config/AiModelConfig.java`
- AI 服务：`ai/BookAssistantAiService.java`
- 上下文：`context/UserContextHolder.java`、`context/UserContextInterceptor.java`、`config/WebMvcConfig.java`、`config/FeignAuthConfig.java`
- Feign：`feign/BookFeignClient.java`、`feign/OrderFeignClient.java`、`feign/dto/*`（5 个）
- 工具：`tool/QueryBookTool.java`、`tool/QueryOrderTool.java`
- 记忆：`config/ChatMemoryConfig.java`、`support/RedisChatMemoryStore.java`
- 业务：`service/ChatService.java`、`service/impl/ChatServiceImpl.java`
- 接口：`controller/AiAssistantController.java`
- 公共：`support/ResultUtils.java`、`dto/ChatRequest.java`、`dto/ChatResponse.java`
- 配置：`nacos-config/ai-assistant.yaml`、`nacos-config/publish.sh`、Gateway 路由
- 测试：`src/test/java/com/bookmall/ai/support/ResultUtilsTest.java`、`src/test/java/com/bookmall/ai/service/impl/ChatServiceImplTest.java`
- 前端：`front/src/views/AiChatView.vue`、`front/src/api/bookmall.js`（`aiApi`）、`front/src/router/index.js`（`/ai` 路由）、`front/src/App.vue`（侧边栏「AI 助手」）

## C.2 验证结果

- 模块单测：`mvn -f BookMall/pom.xml -pl bookmall-ai -am test` ✅ 通过
- 全量编译：`mvn -f BookMall/pom.xml -DskipTests clean package` ✅ 通过
- 前端构建：`cd front && npm run build` ✅ 通过
- 联调冒烟：Vite dev(5173) + mock 网关(8080)，`/ai` 页发送消息 → 用户气泡 + 助手回复气泡正常；停掉网关后再发消息 → 出现 `.alert` 错误提示 + 兜底回复气泡 ✅

## C.3 运行与联调

```bash
export DASHSCOPE_API_KEY='sk-你的通义千问Key'
mvn -f BookMall/pom.xml -DskipTests install
mvn -f BookMall/pom.xml -pl bookmall-ai spring-boot:run
```

先启动 auth / book / cart / stock / order / payment / gateway，再启动 ai-assistant。
验证：`curl http://localhost:8071/ai/hello`；带 JWT `POST /api/ai/chat`，检查搜索与查订单回源。

# 附录 D：前端页面

## D.1 页面与入口

- 页面：`front/src/views/AiChatView.vue`（标题「书小助」）
- 路由：`/ai`（`front/src/router/index.js`，`meta.auth` 需登录）
- 入口：`front/src/App.vue` 侧边栏「AI 助手」链接

## D.2 交互

- 消息区：用户气泡（右侧，强调色）、助手气泡（左侧，浅色）
- 发送：输入框填写 → 回车/点「发送」→ 调用 `POST /api/ai/chat`
- 会话：`conversationId` 存在 `localStorage.bookmall_ai_conversation`，缺省自动生成
- 快捷提问：空白状态展示「推荐几本关于AI的书 / 查一下我的订单 / 有哪些图书分类」
- 「新对话」：清空会话键与消息
- 失败兜底：请求异常时展示 `.alert` 错误提示 + 助手兜底气泡

## D.3 API

- `front/src/api/bookmall.js` 新增 `aiApi.chat({ message, conversationId })`、`aiApi.hello()`
- `/api` 由 Vite 代理到网关 `8080`，网关 `POST /api/ai/chat` → `POST /ai/chat`

---
*本文档为设计说明；实现已落地，对应代码见 `BookMall/bookmall-ai`。*
