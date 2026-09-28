# -*- coding: utf-8 -*-
from docx import Document
from docx.shared import Pt, Inches, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn

doc = Document()

# 设置默认字体
style = doc.styles['Normal']
font = style.font
font.name = '微软雅黑'
font.size = Pt(10.5)
style.element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')

def add_heading(text, level=1):
    h = doc.add_heading(text, level=level)
    for run in h.runs:
        run.font.name = '微软雅黑'
        run.element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
    return h

def add_para(text, bold=False, size=10.5):
    p = doc.add_paragraph()
    run = p.add_run(text)
    run.bold = bold
    run.font.size = Pt(size)
    run.font.name = '微软雅黑'
    run.element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
    return p

def add_code(text):
    p = doc.add_paragraph()
    run = p.add_run(text)
    run.font.name = 'Consolas'
    run.font.size = Pt(9)
    p.paragraph_format.left_indent = Cm(1)
    return p

def add_table(headers, rows):
    table = doc.add_table(rows=1+len(rows), cols=len(headers))
    table.style = 'Light Grid Accent 1'
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, h in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.text = h
        for p in cell.paragraphs:
            for run in p.runs:
                run.bold = True
                run.font.size = Pt(9)
                run.font.name = '微软雅黑'
                run.element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
    for r, row in enumerate(rows):
        for c, val in enumerate(row):
            cell = table.rows[r+1].cells[c]
            cell.text = str(val)
            for p in cell.paragraphs:
                for run in p.runs:
                    run.font.size = Pt(9)
                    run.font.name = '微软雅黑'
                    run.element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
    return table

# ========== 封面 ==========
title = doc.add_heading('', level=0)
run = title.add_run('低代码平台插件机制\n全流程开发指南与风险分析')
run.font.size = Pt(22)
run.font.name = '微软雅黑'
run.element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
title.alignment = WD_ALIGN_PARAGRAPH.CENTER

add_para('')
add_para('版本：v4.0    日期：2026-09-22    模块：bcd-code', size=10)
add_para('整合：元数据驱动 API + 状态触发 + 审批流 + 插件/脚本/表达式三层扩展', size=10)

doc.add_page_break()

# ========== 目录说明 ==========
add_heading('文档结构', 1)
add_para('第一章  风险与问题全面分析')
add_para('第二章  插件开发全流程')
add_para('第三章  插件能调用什么、怎么调用')
add_para('第四章  依赖 JAR 清单')
add_para('第五章  完整示例：库存扣减插件')
add_para('第六章  部署与分发流程')
add_para('第七章  常见问题 FAQ')

doc.add_page_break()

# ========== 第一章 风险 ==========
add_heading('第一章  风险与问题全面分析', 1)

add_heading('1.1 事务化风险', 2)
add_table(
    ['风险', '等级', '说明', '对策'],
    [
        ['事务内 SQL 过多', '高', '同步动作多时，sqlList 可能包含大量 SQL，事务执行时间长，占用数据库连接', '限制单次触发的同步动作数量（如最多 5 个）；监控事务执行时间，超时告警'],
        ['跨数据源无法同事务', '高', 'executeInTransaction 是单连接单数据源，如果主操作在 project 库、动作操作 lowcode 库，无法同事务', '同一触发链的写操作必须在同一数据源；跨源操作改为异步（MQ）'],
        ['读操作在事务外', '中', 'query 在事务编译阶段执行，事务提交前读到的数据可能被其他事务修改', '对一致性要求高的读操作，在动作里用 compileQuery 加入事务（需扩展）'],
        ['脚本绕过事务', '高', 'Groovy 脚本里如果直接调 api.insert() 而非 api.compileInsert()，会绕过事务直接执行', 'PlatformApi 在事务模式下禁用直接执行方法，只暴露 compileXxx'],
        ['事务回滚不彻底', '中', 'executeInTransaction 回滚 DB 操作，但脚本里如果已发 MQ 或调了外部接口，无法回滚', '异步动作在事务提交后才发 MQ；外部接口调用走异步，失败不影主事务'],
    ]
)

add_heading('1.2 插件机制风险', 2)
add_table(
    ['风险', '等级', '说明', '对策'],
    [
        ['ClassLoader 内存泄漏', '高', '卸载插件时未正确关闭 ClassLoader，或插件创建的线程/监听器未清理，导致 Metaspace OOM', '卸载时关闭 URLClassLoader，插件实现 destroy() 生命周期方法清理资源'],
        ['依赖冲突', '高', '插件引入的第三方库版本与平台冲突（如 fastjson 版本不同），运行时 ClassNotFoundException 或 NoSuchMethodError', '插件用独立 ClassLoader 隔离；插件 pom 里公共依赖用 provided scope'],
        ['恶意插件', '高', '插件能执行任意 Java 代码，可删库、读敏感数据、发恶意请求', '第一版不限制但全量审计；后续加 JAR 签名校验 + 代码白名单'],
        ['热加载影响在线请求', '中', '替换 ClassLoader 时，正在执行的请求可能用到旧类，导致异常', '热加载用引用切换（CopyOnWrite），新请求用新 ClassLoader，旧请求执行完再卸载旧'],
        ['插件调试困难', '中', '独立 ClassLoader 里异常堆栈难追踪，日志可能不统一', '插件用 SLF4J 记日志（平台已用）；开发时本地 debug 模式直接挂载 IDE 调试'],
        ['多版本共存', '中', '热更新时旧版未卸载就加载新版，两个版本同时注册', '卸载旧版 ClassLoader 后再加载新版；按 pluginId 唯一管理'],
    ]
)

add_heading('1.3 微服务分发风险', 2)
add_table(
    ['风险', '等级', '说明', '对策'],
    [
        ['Nacos 通知延迟', '中', 'Nacos 配置变更通知有延迟（秒级），部分服务暂时用旧版插件', '可接受，最终一致；紧急情况手动重启服务'],
        ['MinIO 下载失败', '中', '网络问题导致 JAR 下载失败，插件加载失败', '下载失败重试 3 次；失败告警；服务启动时也检查并补加载'],
        ['部分服务加载失败', '中', '3 个实例中 1 个下载失败，导致插件只在 2 个实例生效', 'lowcode_plugin 表记录各服务加载状态；管理页展示加载情况'],
        ['插件 JAR 过大', '低', 'JAR 含大量依赖导致下载慢，影响启动速度', '插件用 provided scope 减小 JAR；大依赖由平台提供'],
        ['target_services 配置错误', '中', '插件配错目标服务，加载到不该加载的服务', '管理页校验服务名合法性；加载日志记录服务名+插件名'],
    ]
)

add_heading('1.4 Groovy 脚本风险', 2)
add_table(
    ['风险', '等级', '说明', '对策'],
    [
        ['编译缓存 Metaspace OOM', '中', '不同脚本编译的 Class 累积，Metaspace 不断增长', '脚本缓存按 MD5 去重；设置缓存上限（如 500 个），LRU 淘汰'],
        ['脚本能访问任意 API', '中', 'Groovy 脚本能调 System.exit()、反射等危险操作', '第一版不限制；后续用 GroovyCodeSource + SecurityManager 限制'],
        ['脚本语法错误运行时才暴露', '低', '脚本存元数据，编译时才发现语法错误', '前端脚本编辑器加语法校验；保存前试编译'],
        ['首次编译慢', '低', 'Groovy 首次编译有开销（几十毫秒）', '编译结果缓存，后续执行直接用缓存的 Class'],
    ]
)

add_heading('1.5 审批流风险', 2)
add_table(
    ['风险', '等级', '说明', '对策'],
    [
        ['递归触发环', '高', '审批通过改状态 → 触发新动作 → 改状态 → 再触发，形成环', 'ActionContext.triggerChain 记录触发链，检测重复中止'],
        ['审批人离线', '中', '审批人不在，审批流卡住', '支持审批委托/转签（后续）；超时自动提醒'],
        ['并发审批', '中', '同一单据被多人同时审批', '审批操作加乐观锁（instance version）；已审批的拒绝重复操作'],
    ]
)

doc.add_page_break()

# ========== 第二章 插件开发全流程 ==========
add_heading('第二章  插件开发全流程', 1)

add_heading('2.1 环境准备', 2)
add_table(
    ['工具', '版本', '说明'],
    [
        ['JDK', '8', '与平台一致'],
        ['Maven', '3.6+', '打包用'],
        ['IDE', 'IDEA / Eclipse', '开发调试'],
        ['plugin-api JAR', '1.0', '平台提供，含接口定义（ActionExecutor、PlatformApi、ActionContext）'],
        ['fastjson2', '2.x', 'JSON 处理（provided，平台已有）'],
    ]
)

add_heading('2.2 创建 Maven 项目', 2)
add_para('项目结构：')
add_code('''my-stock-plugin/
├── pom.xml
└── src/main/java/
    └── com/customer/plugin/
        └── StockAdjustExecutor.java
└── src/main/resources/
    └── META-INF/services/
        └── com.api.lowcode.trigger.action.ActionExecutor''')

add_para('pom.xml：')
add_code('''<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.customer</groupId>
    <artifactId>my-stock-plugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- 平台插件接口 JAR（只含接口定义，不含实现） -->
        <dependency>
            <groupId>com.api</groupId>
            <artifactId>plugin-api</artifactId>
            <version>1.0</version>
            <scope>provided</scope>
        </dependency>
        <!-- fastjson2（平台已有，provided 不打入 JAR） -->
        <dependency>
            <groupId>com.alibaba.fastjson2</groupId>
            <artifactId>fastjson2</artifactId>
            <version>2.0.47</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>''')

add_heading('2.3 实现 ActionExecutor 接口', 2)
add_para('核心：实现 getType() 和 execute() 两个方法。')
add_code('''package com.customer.plugin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.trigger.action.ActionExecutor;
import com.api.lowcode.trigger.action.ActionContext;
import com.api.lowcode.trigger.action.PlatformApi;
import com.server.sqlengine.model.CompiledSql;
import java.util.List;

public class StockAdjustExecutor implements ActionExecutor {

    // 返回动作类型名，前端配置 actionType 时填这个值
    @Override
    public String getType() {
        return "stockAdjust";
    }

    // 执行逻辑：config 是前端配置的参数，ctx 是触发上下文，api 是平台能力
    @Override
    public Object execute(JSONObject config, ActionContext ctx, PlatformApi api) throws Exception {
        String warehouseId = ctx.getNewData().getString("warehouse_id");
        String orderId = ctx.getNewData().getString("order_id");

        // 1. 查库存（读操作，直接执行，不进事务）
        JSONObject queryReq = new JSONObject();
        queryReq.put("tableName", "wms_stock");
        queryReq.put("columns", JSONArray.of("stock_id", "warehouse_id", "material_id", "available_qty"));
        queryReq.put("conditions", buildEqConditions("warehouse_id", warehouseId));
        JSONArray stocks = api.query(queryReq, ctx.getDbName());

        // 2. 编译扣减库存的 SQL（写操作，编译加入事务，不直接执行）
        for (int i = 0; i < stocks.size(); i++) {
            JSONObject stock = stocks.getJSONObject(i);
            int newQty = stock.getIntValue("AVAILABLE_QTY") - 1;

            JSONObject updateReq = new JSONObject();
            updateReq.put("tableName", "wms_stock");
            updateReq.put("data", JSONObject.of("available_qty", newQty));
            updateReq.put("conditions", buildEqConditions("stock_id", stock.getString("STOCK_ID")));

            // 编译模式：返回 CompiledSql，加入事务列表
            CompiledSql sql = api.compileUpdate(updateReq, ctx.getDbName());
            ctx.addSql(sql);  // 加入事务，不直接执行
        }

        // 3. 发消息通知（异步，事务提交后执行）
        // 注意：这里不能直接发，要等事务提交后由平台统一发
        // 异步动作在 actions_json 里单独配 execMode=async

        return JSONObject.of("processed", stocks.size());
    }

    private JSONArray buildEqConditions(String column, Object value) {
        JSONArray conditions = new JSONArray();
        conditions.add(JSONObject.of("Symbol", "and"));
        conditions.add(JSONObject.of("Id", column, "Symbol", "=", "Val", value, "TableAlias", "T"));
        return conditions;
    }
}''')

add_heading('2.4 SPI 注册（自动发现）', 2)
add_para('在 src/main/resources/META-INF/services/ 下创建文件，文件名是接口全名，内容是实现类全名：')
add_code('''# 文件：src/main/resources/META-INF/services/com.api.lowcode.trigger.action.ActionExecutor
# 内容：
com.customer.plugin.StockAdjustExecutor''')
add_para('平台用 ServiceLoader 扫描此文件，自动发现并注册你的实现类，无需额外配置。')

add_heading('2.5 打包', 2)
add_code('''mvn clean package
# 产出：target/my-stock-plugin-1.0.0.jar''')

doc.add_page_break()

# ========== 第三章 能调用什么 ==========
add_heading('第三章  插件能调用什么、怎么调用', 1)

add_heading('3.1 PlatformApi 完整方法清单', 2)
add_table(
    ['方法', '类型', '说明', '是否进事务'],
    [
        ['api.query(req, dbName)', '读', '查询数据库，返回 JSONArray', '否，直接执行'],
        ['api.count(req, dbName)', '读', 'COUNT 查询，返回 long', '否，直接执行'],
        ['api.compileInsert(req, dbName)', '写', '编译 INSERT，返回 CompiledSql', '是，需 ctx.addSql()'],
        ['api.compileUpdate(req, dbName)', '写', '编译 UPDATE，返回 CompiledSql', '是，需 ctx.addSql()'],
        ['api.compileDelete(req, dbName)', '写', '编译 DELETE，返回 CompiledSql', '是，需 ctx.addSql()'],
        ['api.executeInTransaction(dbName, sqlList)', '事务', '一次性提交所有 SQL', '平台统一调用'],
        ['api.redisGet(key)', '缓存', '读 Redis', '否'],
        ['api.redisSet(key, value, ttl)', '缓存', '写 Redis', '否'],
        ['api.sendMq(topic, message)', '消息', '发 RocketMQ', '否（异步动作用）'],
        ['api.getModel(modelCode)', '元数据', '读模型元数据（走缓存）', '否'],
        ['api.executeApi(apiCode, body)', '引擎', '调元数据驱动 API', '否'],
    ]
)

add_heading('3.2 怎么调用数据库', 2)
add_para('查询（读操作，直接执行）：')
add_code('''JSONObject req = new JSONObject();
req.put("tableName", "wms_stock");
req.put("columns", JSONArray.of("stock_id", "available_qty"));
req.put("conditions", buildEqConditions("warehouse_id", "wh001"));
req.put("page", 1);
req.put("pageSize", 100);
JSONArray result = api.query(req, "project");  // dbName=project 业务库''')

add_para('新增（写操作，编译加入事务）：')
add_code('''JSONObject req = new JSONObject();
req.put("tableName", "wms_outbound_order");
req.put("data", JSONObject.of("order_no", "OUT001", "warehouse_id", "wh001", "status", "draft"));
CompiledSql sql = api.compileInsert(req, ctx.getDbName());
ctx.addSql(sql);  // 加入事务，由平台统一 executeInTransaction''')

add_para('修改（写操作，编译加入事务）：')
add_code('''JSONObject req = new JSONObject();
req.put("tableName", "wms_stock");
req.put("data", JSONObject.of("available_qty", 99));
req.put("conditions", buildEqConditions("stock_id", "stk001"));
CompiledSql sql = api.compileUpdate(req, ctx.getDbName());
ctx.addSql(sql);''')

add_para('删除（写操作，编译加入事务）：')
add_code('''JSONObject req = new JSONObject();
req.put("tableName", "wms_temp_record");
req.put("conditions", buildEqConditions("order_id", "ORD001"));
CompiledSql sql = api.compileDelete(req, ctx.getDbName());
ctx.addSql(sql);''')

add_heading('3.3 怎么调用缓存', 2)
add_code('''// 读 Redis
String cached = api.redisGet("stock:wh001:mat001");

// 写 Redis（TTL 1 小时）
api.redisSet("stock:wh001:mat001", "100", 3600);''')

add_heading('3.4 怎么发消息', 2)
add_code('''// 发 RocketMQ（异步动作里用，事务提交后执行）
api.sendMq("stock-adjusted", JSONObject.of("orderId", ctx.getBizId()));''')

add_heading('3.5 怎么读元数据', 2)
add_code('''// 读模型元数据（走 L1CaffeineCache）
JSONObject model = api.getModel("wms_inbound_order");
JSONArray fields = JSON.parseArray(model.getString("fieldsJson"));''')

add_heading('3.6 怎么调元数据驱动 API', 2)
add_code('''// 调用平台已配置的 API（如调"查仓库详情"接口）
Object result = api.executeApi("warehouse_detail", JSONObject.of("warehouseId", "wh001"));''')

add_heading('3.7 事务模式核心规则', 2)
add_table(
    ['场景', '用什么', '为什么'],
    [
        ['查数据', 'api.query()', '读操作不需要事务保护，直接执行最快'],
        ['增删改数据（同步动作）', 'api.compileXxx() + ctx.addSql()', '编译加入事务列表，平台统一 executeInTransaction，保证原子性'],
        ['发消息/调外部接口', 'api.sendMq()（异步动作里用）', '事务提交后才发，避免事务回滚但消息已发'],
        ['缓存读写', 'api.redisGet/Set()', '缓存不进事务，独立操作'],
    ]
)

doc.add_page_break()

# ========== 第四章 依赖 JAR ==========
add_heading('第四章  依赖 JAR 清单', 1)

add_heading('4.1 插件项目需要的 JAR', 2)
add_table(
    ['JAR', 'scope', '说明', '哪里获取'],
    [
        ['plugin-api-1.0.jar', 'provided', '平台接口定义（ActionExecutor、PlatformApi、ActionContext、CompiledSql）', '平台项目 bcd-code 打包产出，或 Maven 私服'],
        ['fastjson2-2.x.jar', 'provided', 'JSONObject/JSONArray JSON 处理', 'Maven 中央仓库'],
        ['slf4j-api.jar', 'provided', '日志（可选，插件记日志用）', 'Maven 中央仓库'],
    ]
)
add_para('关键：所有依赖用 provided scope，不打入插件 JAR，由平台运行时提供。插件 JAR 只含你的业务代码。')

add_heading('4.2 平台需要新增的依赖', 2)
add_table(
    ['依赖', '版本', '大小', '用途', '必要性'],
    [
        ['org.pf4j:pf4j', '3.9.0', '~200KB', '插件框架（热加载/卸载/ClassLoader 隔离）', '推荐，也可自实现简版'],
        ['org.codehaus.groovy:groovy-all', '2.4.21', '~7MB', '脚本引擎（Groovy，语法兼容 Java）', '脚本扩展用'],
        ['com.googlecode.aviator:aviator', '5.3.3', '~500KB', '表达式引擎（字段计算/条件判断）', '表达式扩展用'],
    ]
)

add_heading('4.3 plugin-api JAR 怎么产出', 2)
add_para('平台 bcd-code 项目里，把接口定义抽到独立模块 plugin-api：')
add_code('''plugin-api/
└── src/main/java/com/api/lowcode/trigger/action/
    ├── ActionExecutor.java      -- 动作执行器接口
    ├── ActionContext.java        -- 动作上下文
    └── PlatformApi.java          -- 平台能力接口
└── src/main/java/com/server/sqlengine/model/
    └── CompiledSql.java          -- 编译后的 SQL（已在 bcd-wms-db 中）''')
add_para('mvn install 打成 plugin-api-1.0.jar，给插件项目依赖。')

doc.add_page_break()

# ========== 第五章 完整示例 ==========
add_heading('第五章  完整示例：库存扣减插件', 1)

add_heading('5.1 业务场景', 2)
add_para('入库单状态从 draft 改为 confirmed（下达）时，扣减对应仓库的可用库存，并生成一条库存变动记录。')

add_heading('5.2 完整代码', 2)
add_code('''package com.customer.plugin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.trigger.action.ActionExecutor;
import com.api.lowcode.trigger.action.ActionContext;
import com.api.lowcode.trigger.action.PlatformApi;
import com.server.sqlengine.model.CompiledSql;

public class StockDeductExecutor implements ActionExecutor {

    @Override
    public String getType() {
        return "stockDeduct";
    }

    @Override
    public Object execute(JSONObject config, ActionContext ctx, PlatformApi api) throws Exception {
        JSONObject newData = ctx.getNewData();
        String warehouseId = newData.getString("warehouse_id");
        String orderId = newData.getString("order_id");
        String dbName = ctx.getDbName();

        // === 第1步：查入库单明细（读操作，直接执行） ===
        JSONObject detailReq = new JSONObject();
        detailReq.put("tableName", "wms_inbound_order_detail");
        detailReq.put("columns", JSONArray.of("material_id", "qty"));
        detailReq.put("conditions", buildEq("order_id", orderId));
        JSONArray details = api.query(detailReq, dbName);

        if (details.isEmpty()) {
            throw new RuntimeException("入库单无明细: " + orderId);
        }

        // === 第2步：逐条扣减库存（写操作，编译加入事务） ===
        int processed = 0;
        for (int i = 0; i < details.size(); i++) {
            JSONObject detail = details.getJSONObject(i);
            String materialId = detail.getString("MATERIAL_ID");
            int qty = detail.getIntValue("QTY");

            // 查当前库存
            JSONObject stockReq = new JSONObject();
            stockReq.put("tableName", "wms_stock");
            stockReq.put("columns", JSONArray.of("stock_id", "available_qty"));
            stockReq.put("conditions", buildEq2("warehouse_id", warehouseId, "material_id", materialId));
            JSONArray stocks = api.query(stockReq, dbName);

            if (stocks.isEmpty()) {
                throw new RuntimeException("库存记录不存在: 仓库=" + warehouseId + " 物料=" + materialId);
            }

            JSONObject stock = stocks.getJSONObject(0);
            int currentQty = stock.getIntValue("AVAILABLE_QTY");
            if (currentQty < qty) {
                throw new RuntimeException("库存不足: 物料=" + materialId + " 当前=" + currentQty + " 需要=" + qty);
            }

            // 编译扣减 SQL（加入事务）
            JSONObject updateReq = new JSONObject();
            updateReq.put("tableName", "wms_stock");
            updateReq.put("data", JSONObject.of("available_qty", currentQty - qty));
            updateReq.put("conditions", buildEq("stock_id", stock.getString("STOCK_ID")));
            CompiledSql updateSql = api.compileUpdate(updateReq, dbName);
            ctx.addSql(updateSql);

            // 编译库存变动记录 SQL（加入事务）
            JSONObject logReq = new JSONObject();
            logReq.put("tableName", "wms_stock_log");
            logReq.put("data", JSONObject.of(
                "stock_id", stock.getString("STOCK_ID"),
                "order_id", orderId,
                "change_qty", -qty,
                "before_qty", currentQty,
                "after_qty", currentQty - qty,
                "type", "inbound_deduct"
            ));
            CompiledSql logSql = api.compileInsert(logReq, dbName);
            ctx.addSql(logSql);

            processed++;
        }

        return JSONObject.of("success", true, "processed", processed);
    }

    private JSONArray buildEq(String column, Object value) {
        JSONArray conditions = new JSONArray();
        conditions.add(JSONObject.of("Symbol", "and"));
        conditions.add(JSONObject.of("Id", column, "Symbol", "=", "Val", value, "TableAlias", "T"));
        return conditions;
    }

    private JSONArray buildEq2(String col1, Object val1, String col2, Object val2) {
        JSONArray conditions = new JSONArray();
        conditions.add(JSONObject.of("Symbol", "and"));
        conditions.add(JSONObject.of("Id", col1, "Symbol", "=", "Val", val1, "TableAlias", "T"));
        conditions.add(JSONObject.of("Symbol", "and"));
        conditions.add(JSONObject.of("Id", col2, "Symbol", "=", "Val", val2, "TableAlias", "T"));
        return conditions;
    }
}''')

add_heading('5.3 SPI 注册文件', 2)
add_code('''# 文件：src/main/resources/META-INF/services/com.api.lowcode.trigger.action.ActionExecutor
com.customer.plugin.StockDeductExecutor''')

add_heading('5.4 前端配置', 2)
add_para('在状态触发配置里，配置动作：')
add_code('''{
  "actionType": "stockDeduct",
  "name": "扣减库存",
  "execMode": "sync",
  "failStrategy": "rollback",
  "config": {}
}''')
add_para('当入库单状态从 draft 变为 confirmed 时，平台自动调用此插件，扣减库存 + 写变动记录，全部在同一个事务里。')

doc.add_page_break()

# ========== 第六章 部署 ==========
add_heading('第六章  部署与分发流程', 1)

add_heading('6.1 全流程图', 2)
add_code('''开发插件
  ↓ mvn package
打 JAR
  ↓ 上传到平台设计器
插件管理页 → POST /api/lowcode/plugin/upload
  ↓
  ① 存 JAR 到 MinIO
  ② 更新 lowcode_plugin 表
  ③ 更新 Nacos 配置 lowcode.plugins
  ↓ Nacos 广播
各服务监听 Nacos 变更
  ↓ 按 target_services 过滤
  ↓ checksum 对比（判断是否需更新）
  ↓ 从 MinIO 下载 JAR
  ↓ PluginManagerService 热加载
  ↓
插件生效，前端配置 actionType 即可使用''')

add_heading('6.2 上传插件', 2)
add_para('方式一：设计器插件管理页上传（推荐）')
add_para('方式二：API 上传')
add_code('''POST /api/lowcode/plugin/upload
Content-Type: multipart/form-data

file: my-stock-plugin-1.0.0.jar
pluginId: stock-deduct
version: 1.0.0
targetServices: bcd-code,bcd-wms''')

add_heading('6.3 管理插件', 2)
add_table(
    ['操作', '接口', '说明'],
    [
        ['查看列表', 'POST /api/lowcode/plugin/list', '所有已上传插件'],
        ['启用', 'POST /api/lowcode/plugin/{pluginId}/enable', 'Nacos enabled=true，各服务加载'],
        ['禁用', 'POST /api/lowcode/plugin/{pluginId}/disable', 'Nacos enabled=false，各服务卸载'],
        ['卸载', 'DELETE /api/lowcode/plugin/{pluginId}', '删除记录 + MinIO JAR + Nacos 配置'],
    ]
)

add_heading('6.4 微服务分发机制', 2)
add_table(
    ['环节', '机制', '说明'],
    [
        ['存储', 'MinIO', 'JAR 存 MinIO，URL 记录在 lowcode_plugin.jar_url'],
        ['广播', 'Nacos 配置 lowcode.plugins', '插件列表 JSON，变更自动通知各服务'],
        ['过滤', 'target_services', '每个服务只加载属于自己的插件'],
        ['去重', 'checksum (MD5)', '相同 checksum 不重复下载加载'],
        ['拉取', 'MinIO 下载', '各服务从 MinIO 下载 JAR 到本地'],
        ['加载', 'PluginManagerService', 'URLClassLoader + ServiceLoader 热加载'],
        ['卸载', 'ClassLoader.close()', '关闭旧 ClassLoader 释放资源'],
    ]
)

doc.add_page_break()

# ========== 第七章 FAQ ==========
add_heading('第七章  常见问题 FAQ', 1)

faqs = [
    ('Q: 插件能直接连数据库吗？',
     'A: 不能直接连。必须通过 PlatformApi 的 query/compileInsert/compileUpdate/compileDelete 方法访问数据库。这样平台能做字段白名单校验、SQL 注入防护、事务管理。'),
    ('Q: 插件能用 Spring 注解吗？',
     'A: 不能。插件在独立 ClassLoader 里，不在 Spring 容器中。通过 PlatformApi 参数访问平台能力，不(a)不用 @Resource/@Autowired。'),
    ('Q: 插件能引入第三方库吗？',
     'A: 能。在插件 pom.xml 里声明依赖，会打入插件 JAR。但注意版本冲突风险，公共库（fastjson2、slf4j）用 provided scope 由平台提供。'),
    ('Q: 插件怎么调试？',
     'A: 开发时在 IDE 里直接 debug。把插件项目依赖平台源码（bcd-code），本地启动平台，在插件代码里打断点。生产环境用日志（SLF4J）排查。'),
    ('Q: 同步动作和异步动作的区别？',
     'A: 同步动作（execMode=sync）在事务内执行，用 compileXxx 编译 SQL 加入事务，失败可回滚。异步动作（execMode=async）在事务提交后通过 RocketMQ 执行，用于通知/外部接口调用，失败不影响主事务。'),
    ('Q: 一个触发可以配多个动作吗？',
     'A: 能。actions_json 是数组，按顺序执行。同步动作按顺序编译 SQL 加入事务，最后一次性提交。异步动作逐个发 MQ。'),
    ('Q: 插件更新后需要重启服务吗？',
     'A: 不需要。上传新版本 JAR → Nacos 广播 → 各服务自动卸载旧版 + 加载新版，热更新不重启。'),
    ('Q: 插件能调另一个微服务吗？',
     'A: 能。用 api.sendMq() 发消息让目标服务处理，或在插件里用 HTTP 客户端（如 OkHttp）直接调。但 HTTP 调用建议放异步动作，避免阻塞事务。'),
    ('Q: 脚本和插件怎么选？',
     'A: 简单逻辑（几十行以内、单文件）用 Groovy 脚本，前端编辑存元数据。复杂业务（多文件、需要完整 Java 项目、调试复杂）用插件 JAR。'),
    ('Q: 事务里 SQL 太多怎么办？',
     'A: 限制单次触发的同步动作数量（建议不超过 5 个）。如果确实需要大量写操作，考虑拆分为多个触发或用异步批量处理。'),
]

for q, a in faqs:
    add_para(q, bold=True)
    add_para(a)
    add_para('')

# ========== 总结 ==========
doc.add_page_break()
add_heading('总结', 1)
add_para('插件机制是低代码平台的核心扩展能力，关键要点：')
add_para('1. 插件只依赖 plugin-api JAR（接口定义），不依赖平台实现，不耦合 Spring')
add_para('2. 通过 PlatformApi 访问平台能力（数据库/缓存/MQ/元数据），不直接连资源')
add_para('3. 写操作用 compileXxx 编译加入事务，保证原子性；读操作直接执行')
add_para('4. SPI 自动发现，打 JAR 上传即用，Nacos 广播分发到各微服务')
add_para('5. 热加载不重启，ClassLoader 隔离避免依赖冲突')
add_para('6. 三层扩展：表达式（简单）→ 脚本（中等）→ 插件（复杂），按需选择')

output_path = r'D:\Documents\qwen-agent\code\code-service\lowcode-platform-plan\插件开发全流程与风险分析.docx'
doc.save(output_path)
print(f'文档已生成：{output_path}')