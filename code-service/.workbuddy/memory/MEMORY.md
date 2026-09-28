# bcd-wms 项目记忆

## 项目概况
- SaaS WMS（仓库管理系统），Maven 多模块工程，根 pom groupId `saas-wms`，版本 1.0-1
- 技术栈：Java 8 · Spring Boot 2.3.12 · Spring Cloud Hoxton.SR12 · Spring Cloud Alibaba 2.2.7（Nacos 注册+配置、Sentinel、OpenFeign）
- 中间件：Redis(Redisson 4.4.0) · MinIO · SkyWalking 9.3 · WebSocket · CXF(WebService) · Axis/WSDL
- 数据库多支持：PostgreSQL / Oracle(ojdbc8) / 达梦(DmJdbcDriver8)，ORM 走自研 template 模块
- 部署：docker-compose（Nacos 2.1.0 standalone + 全部服务）+ Windows 服务（serviceRelease + InstallService.exe/WinSW 风格 xml）双通道；fat jar 与 thin jar（-Dloader.path）两种打包并存

## 模块分层
- 基础层：common（公共类）→ template（自研 ORM/基类，com.base 包：MasterPage/BasePage/GridUtil 等）→ db
- 业务服务（Web 端）：in(8009 入库/质检/退货) · out(8007 出库/拣货/装车) · stock(8012 库存/盘点/冻结) · tms(8011 运输) · system(8005) · report(8021 报表) · base(8015 基础资料) · combox(8010) · ifs(8058) · orderflow(7256) · websocket(8025)
- API 对接层：api-in(8013) · api-out(8014) · api-stock(8019) · api-system(8008) · api-tms(8006) · api-wcs(8024) · api-client(8023) · api-ops(8123)
- 入口：gateway(8004) · auth(8003 JWT)
- 代码风格：包名 com.api.controller/service/entity/feign，按表名小写下划线分包（如 in_mst、stock_det）

## 环境配置
- Nacos：172.29.241.131:8001，dev 环境 discovery namespace=lw / config namespace=wms；账号密码在根 pom profile 中（敏感，勿外传）
- Maven profile（四选一，无默认激活）：debug / dev / test / prod。debug 的 prefix/envName 都为空（兼容旧路由表裸服务名 `bcd-xxx`），dev/test/prod 的 prefix=`-`、envName 分别为 dev/test/prod。根 pom 删了 dev 的 activeByDefault，IDEA 工具栏勾哪个生效哪个，不会偷摸激活 dev
- 两套占位符体系：pom 里 `${prefix}`/`${envName}` 是 Maven 构建时属性；yml/Java 里 `${app.prefix}`/`${app.env}` 是 Spring 运行时属性。桥接点是 yml 的 `app.env: @envName@` 和 `app.prefix: '@prefix@'` 两行（Maven 资源过滤替换 @..@ 后注入给 Spring）。`@prefix@` 必须加单引号防 SnakeYAML 把 `-` 当 list 项
- 方案 C（已落地）：服务名 `spring.application.name`、skywalking service-name、FeignClient name 全用运行时占位符 `${app.prefix}${app.env}`，不依赖 Maven 编译时替换。IDEA 调试时在 Run Configuration 的 VM options 加 `-Dapp.env=<env> -Dapp.prefix=<>` 覆盖 target/classes 里残留旧值即可切环境，无需 Rebuild。打包部署走 `mvn -P<env> package`，Maven 替换 `@envName@`/`@prefix@` 给 Spring 当默认值
- 目标目录按环境隔离：`<directory>target${prefix}${envName}</directory>`，maven-clean-plugin 3.4.0 绑 initialize 阶段，`mvn package` 自动先清空
- 根目录 Qwen3/ 是独立的 FastAPI OCR 服务（vLLM 视觉模型，书籍 OCR），与 Java 主工程无 Maven 关联
- 构建产物、lib 依赖 jar、target 目录都提交在工作区内（部署型仓库风格）

## 认证/登录链路（2026-09-02 B 方案落地）
- auth 服务用 Spring Security OAuth2（`@EnableAuthorizationServer` + `JdbcAuthorizationServerConfig`），JWT 签名（jwt.jks 密钥对，alias=bcd，密码=Ahbcd0306），client 走 `DynamicClientDetailsService` 从 DB 动态加载（单租户 JdbcClientDetailsService，多租户 HTTP 调租户管理服务 `/auth/checkClient`），autoapprove 未配置（默认 false，会弹授权页）
- 登录链路改造前：`HttpClientAuthor` 手写 HttpClient 模拟 OAuth2 授权码 4 步（/login → /oauth/authorize 申请 → 模拟点同意 → /oauth/token 换码），`param.url=http://localhost:8003` 硬编码绕过 Nacos
- 登录链路改造后（B 方案）：auth 暴露 `/internal/auth/login` 内部接口（`InternalAuthController`），直接 `AuthenticationManager.authenticate()` 校验 + `tokenService.createAccessToken()` 签 JWT，跳过 OAuth2 套娃；调用方用 `AuthFeignClient`（`@FeignClient("bcd-wms-auth${app.prefix}${app.env}")`）走 Nacos 服务发现
- 三个调用点：`LoginSysService`(api-system,明文密码+tenant) / `Login`(system,RSA 解密密码) / `SupplierLogin`(system,不传 tenant,auth 兜底"sysid")；FeignClient 在 `bcd-wms-common/com.common.feign.AuthFeignClient`，主类 `@EnableFeignClients(basePackages={"com.api","com.common"})`
- 安全约束：`/internal/**` 在 `SpringSecurityConfig` 放行，但 gateway 不转发该路径，生产建议加内网 IP 白名单
- 遗留：`HttpClientAuthor.java` 和 `pom.xml` 四个 profile 的 `param.url` 已无引用未删；Yms/Oms 两项目同模式未改
