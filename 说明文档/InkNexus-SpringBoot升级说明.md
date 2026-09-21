# InkNexus Spring Boot 升级说明

> 升级日期：2026-09-19。本次为纯框架升级，无业务代码改动；`inknexus-ai` 暂保留 LangChain4j 1.19.0，后续另做 Spring AI 迁移（Spring AI 1.1.x 要求 Boot 3.4+，本次升级为其铺路）。

## 1. 版本对照

| 组件 | 升级前 | 升级后 | 说明 |
|---|---|---|---|
| Spring Boot | 3.2.5 | **3.5.16** | 3.5 线最新补丁（Maven Central 已核实） |
| Spring Cloud | 2023.0.2 | **2025.0.3** | 与 Boot 3.5.x 配对 |
| Spring Cloud Alibaba | 2023.0.1.0 | **2025.0.0.0** | 官方基线即 Boot 3.5 / SC 2025.0.x；自带 nacos-client 3.0.3 |
| springdoc-openapi | 2.2.x（knife4j 内嵌） | **2.8.17**（父 POM 覆盖） | knife4j 4.5.0 内嵌的 2.2.x 与 Framework 6.2 不兼容 |
| Knife4j | 4.5.0 | 4.5.0（不变） | 已是最新发布版，靠覆盖 springdoc 适配 |
| LangChain4j | 1.19.0 | 1.19.0（不变） | 编译与单测在新版本下均通过 |
| MyBatis-Plus / JJWT / Java | 3.5.14 / 0.11.5 / 17 | 不变 | 与 Boot 3.5 兼容 |

版本选择依据：Spring Boot / Spring Cloud / SCA 的 `maven-metadata.xml`（repo1.maven.org）与 SCA 官方版本说明（sca.aliyun.com）。SCA 2025.0.0.0 的 starter POM 实际编译基线为 Boot 3.5.0 / Spring Cloud 4.3.0，配对关系正确。

## 2. 实际改动点

仅 `InkNexus/pom.xml`（父 POM）一处：

```properties
<spring-boot.version>3.5.16</spring-boot.version>        <!-- 3.2.5 -->
<spring-cloud.version>2025.0.3</spring-cloud.version>    <!-- 2023.0.2 -->
<spring-cloud-alibaba.version>2025.0.0.0</spring-cloud-alibaba.version> <!-- 2023.0.1.0 -->
<springdoc.version>2.8.17</springdoc.version>            <!-- 新增 -->
```

并在 `dependencyManagement` 新增三个 springdoc 坐标（`springdoc-openapi-starter-common` / `-webmvc-api` / `-webmvc-ui`）统一到 `${springdoc.version}`，覆盖 knife4j 传递依赖的 2.2.x。9 个业务模块的 POM 零改动。

## 3. 验证记录（macOS ARM64，本机 Docker 基础设施）

1. **编译**：`mvn -f InkNexus/pom.xml clean package -DskipTests` → 10 个模块 BUILD SUCCESS。
2. **单测**：`mvn -f InkNexus/pom.xml test` → 全部 81 个用例通过（common 9 / auth 4 / book 17 / cart 3 / stock 13 / gateway 10 / order 15 / payment 3 / ai 7）。
3. **运行冒烟（inknexus-auth）**：
   - 服务正常启动：`Started AuthApplication in 2.584 seconds`；
   - **nacos-client 3.0.3 ↔ Nacos Server v2.5.4 注册成功**：`nacos registry, DEFAULT_GROUP auth ... register finished`（这是本次升级最大的不确定项，已验证兼容）；
   - **springdoc 2.8.17 + knife4j 4.5.0 运行时正常**：`GET /v3/api-docs` 返回 HTTP 200、OpenAPI 3.1 JSON。

## 4. 遗留事项与注意事项

- nacos-client 已升级到 3.0.3，本地 Nacos Server 保持 v2.5.4 即可（注册已实测）；若后续遇到配置监听等异常，回退方案：把 `docker-compose.infra.yml` 的 Nacos 镜像升到 v3.x，或在父 POM 锁回 `nacos-client` 2.5.x。
- Knife4j 的 UI 页面（`http://localhost:<端口>/doc.html`）建议在下次启动各服务时顺手点开复核一次；本次已验证其数据源 `/v3/api-docs` 正常。
- Spring Cloud Gateway 在 SC 2025.0.3 下编译与单测通过，首次启动网关时留意 Netty native 库加载（`macos-arm64` profile 保留有效）。
- 全链路手工回归（注册→登录→下单→支付→超时关单→AI 问答）尚未执行，属于下次联调时顺带验证项。

## 5. 下一步

将 `inknexus-ai` 从 LangChain4j 迁移到 Spring AI 1.1.5（`spring-ai-starter-model-deepseek`，接 DeepSeek API `deepseek-chat`）——已于 2026-09-19 完成，见 `InkNexus-SpringAI迁移说明.md`。
