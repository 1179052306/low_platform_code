package com.api.lowcode.common;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.dialect.SqlDialect;
import com.server.sqlengine.dialect.SqlDialectRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 编码规则引擎：解析前端 NumberRuleDialog 生成的多段编码配置 JSON（{"segments":[...]}），
 * 在 create 时为空值字段生成业务编码。
 *
 * <p>支持的段类型：constant 常量 / random 随机码 / sysDate 系统日期 / bizDate 业务对象日期 /
 * bizField 业务对象字段 / sequence 流水号。段间分隔符取后一段的 separator。</p>
 *
 * <p>流水号段三开关：
 * <ul>
 *   <li>seqZeroPad 用0补位：不足 seqLength 时前导补零（默认开）</li>
 *   <li>seqAutoExpand 超过长度自动升位：关闭时流水号位数超过 seqLength 抛 BizException；
 *       开启时允许自然增长不截断（关闭时会截断会重号，必须报错）</li>
 *   <li>seqGapFill 断号补偿：开启时删除记录回收流水号入 lc_sequence_gap 池，
 *       发号优先从池中取最小号复用</li>
 * </ul></p>
 *
 * <p>发号表 lc_sequence_no(seq_key 唯一 + UPDATE 原子自增)，按 bizCode+字段+编码前缀+重置周期
 * （DAILY/MONTHLY/YEARLY/GLOBAL）分键计数；回收池 lc_sequence_gap(seq_key, no)。</p>
 */
@Slf4j
@Component
public class NumberRuleEngine {

    /** 发号直连数据源（与 TxConfig 事务管理器绑定的同一 lowcode 库，避免路由数据源 autocommit 竞态） */
    private DataSource txDataSource;

    /** 发号事务模板：REQUIRED，加入外层业务事务，FOR UPDATE 行锁持到业务提交 */
    private TransactionTemplate txTemplate;

    /** 可失败写模板：REQUIRES_NEW 独立小事务——INSERT 撞唯一键只回滚本小事务，
     *  不毒化外层业务事务（PG 下事务内语句失败会将整个事务标记为 aborted，后续 SQL 全部 25P02） */
    private TransactionTemplate failSafeTxTemplate;

    @Resource
    private SqlDialectRegistry dialectRegistry;

    private static final String DB_NAME = "lowcode";
    private static final String SEQ_TABLE = "lc_sequence_no";
    private static final String GAP_TABLE = "lc_sequence_gap";

    private volatile boolean seqTableChecked = false;

    @Resource
    public void setTxManager(PlatformTransactionManager txManager) {
        this.txDataSource = ((DataSourceTransactionManager) txManager).getDataSource();
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setTimeout(5);
        this.txTemplate = t;
        TransactionTemplate fs = new TransactionTemplate(txManager);
        fs.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        fs.setTimeout(5);
        this.failSafeTxTemplate = fs;
    }

    /**
     * 按编码规则生成编码值。
     *
     * @param expr      编码规则 JSON（{"segments":[...]}）
     * @param data      本次请求数据（bizDate/bizField 段从中取值）
     * @param bizCode   业务编码（发号键组成部分）
     * @param fieldName 目标字段名（发号键组成部分）
     * @return 生成的编码；解析失败返回 null（调用方跳过，不阻断保存）
     */
    public String generate(String expr, JSONObject data, String bizCode, String fieldName) {
        JSONObject config;
        try {
            config = JSONObject.parseObject(expr);
        } catch (Exception e) {
            log.warn("[NumberRule] 表达式解析失败 bizCode={} field={} expr={}", bizCode, fieldName, expr);
            return null;
        }
        JSONArray segments = config != null ? config.getJSONArray("segments") : null;
        if (segments == null || segments.isEmpty()) return null;

        // 逐段渲染（段数任意）；prefix 为发号键固定部分 = 非 random 非 sequence 段内容
        StringBuilder result = new StringBuilder();
        StringBuilder prefix = new StringBuilder();
        int seqCount = 0;
        for (int i = 0; i < segments.size(); i++) {
            JSONObject seg = segments.getJSONObject(i);
            if (seg == null) continue;
            String type = seg.getString("type");
            boolean isRandom = "random".equals(type);
            boolean isSeq = "sequence".equals(type);
            // 非首内容段：先拼该段配置的 separator（random/seq 段分隔符不入发号键 prefix）
            if (result.length() > 0) {
                String sep = seg.getString("separator");
                if (sep != null && !sep.isEmpty()) {
                    result.append(sep);
                    if (!isRandom && !isSeq) prefix.append(sep);
                }
            }
            if (isSeq) {
                // 多流水号段：逐段独立发号；第 2 个起发号键追加段索引区分（首个保持旧键格式，向后兼容已发号数据）
                seqCount++;
                result.append(nextSequence(seg, bizCode, fieldName, prefix.toString(), seqCount));
            } else {
                String part = renderSegment(seg, type, data);
                if (part == null) part = "";
                result.append(part);
                if (!isRandom) prefix.append(part);
            }
        }
        return result.toString();
    }

    /**
     * 断号回收：按段结构解析原编码，提取流水号入 lc_sequence_gap 池。
     * 仅当规则含流水号段且 seqGapFill=true 时生效。
     *
     * @param expr      编码规则 JSON
     * @param code      被删除记录的编码值
     * @param data      被删除记录的行数据（bizField 段长度复算用）
     * @param bizCode   业务编码
     * @param fieldName 字段名
     */
    public void recycle(String expr, String code, JSONObject data, String bizCode, String fieldName) {
        if (expr == null || code == null || code.isEmpty()) return;
        JSONObject config;
        try {
            config = JSONObject.parseObject(expr);
        } catch (Exception e) {
            return;
        }
        JSONArray segments = config != null ? config.getJSONArray("segments") : null;
        if (segments == null || segments.isEmpty()) return;

        // 逐段消费原编码（与 generate 渲染顺序严格对称）：常规段按渲染长度消费，
        // 流水号段用数字前缀正则切分（升位后长度不定，数字前缀是唯一可靠边界），逐段独立回收。
        // prefixAcc 边消费边累积（仅非 random 非 seq 段内容+其分隔符）——与 generate 的发号键逐段一致，
        // 保证第 k 个流水号段的回收键 = 发号键（多流水号段各自独立，互不混键）。
        String rest = code;
        String scopeDateStr = null; // 最近一个 sysDate 段的原值，用于恢复各流水号段发号时的 scopeKey
        int consumedChars = 0;      // 已消费字符数（与 generate 的 result.length() 对应，判定段前分隔符）
        StringBuilder prefixAcc = new StringBuilder();
        int seqCount = 0;
        for (int i = 0; i < segments.size(); i++) {
            JSONObject seg = segments.getJSONObject(i);
            if (seg == null) continue;
            String type = seg.getString("type");
            boolean isSeq = "sequence".equals(type);
            // 段前分隔符（首段无；seq/random 段的分隔符消费但不入发号键）
            String sep = seg.getString("separator");
            if (consumedChars > 0 && sep != null && !sep.isEmpty()) {
                if (!rest.startsWith(sep)) {
                    log.warn("[NumberRule] 断号回收解析失败（分隔符不匹配）: code={} sep={}", code, sep);
                    return;
                }
                rest = rest.substring(sep.length());
                consumedChars += sep.length();
                if (!isSeq && !"random".equals(type)) prefixAcc.append(sep);
            }
            if (isSeq) {
                seqCount++;
                // 切出流水号文本：最长数字前缀（容忍前导补零与升位变长）
                java.util.regex.Matcher m = SEQ_TEXT_PATTERN.matcher(rest);
                if (!m.find() || m.group().isEmpty()) {
                    log.warn("[NumberRule] 断号回收解析失败（流水号非数字）: code={} rest={}", code, rest);
                    return;
                }
                String seqText = m.group();
                long no;
                try {
                    no = Long.parseLong(seqText);
                } catch (NumberFormatException e) {
                    return;
                }
                if (seg.getBooleanValue("seqGapFill")) {
                    String scope = seg.getString("seqScope");
                    LocalDate scopeDate = parseScopeDate(scopeDateStr, scope);
                    String seqKey = bizCode + ":" + fieldName + ":" + prefixAcc + ":" + scopeKeyOf(scope, scopeDate);
                    if (seqCount > 1) seqKey += ":" + seqCount;
                    insertGap(seqKey, no);
                }
                rest = rest.substring(seqText.length());
                consumedChars += seqText.length();
                continue;
            }
            int consume;
            switch (type == null ? "" : type) {
                case "constant":
                    consume = safeLen(seg.getString("value"));
                    break;
                case "random":
                    consume = seg.getIntValue("length") > 0 ? Math.min(seg.getIntValue("length"), 32) : 8;
                    break;
                case "sysDate": {
                    String fmt = seg.getString("dateFormat");
                    consume = dateFmtLen(fmt);
                    if (rest.length() >= consume) scopeDateStr = rest.substring(0, consume);
                    break;
                }
                case "bizDate":
                    consume = dateFmtLen(seg.getString("dateFormat"));
                    break;
                case "bizField": {
                    String v = renderSegment(seg, "bizField", data);
                    consume = v != null ? v.length() : 0;
                    break;
                }
                default:
                    consume = 0;
            }
            if (rest.length() < consume) {
                log.warn("[NumberRule] 断号回收解析失败（长度不足）: code={}", code);
                return;
            }
            if (consume > 0 && !"random".equals(type)) {
                prefixAcc.append(rest, 0, consume);
            }
            rest = rest.substring(consume);
            consumedChars += consume;
        }
    }

    /** 流水号文本边界：最长数字前缀（0*\d+），升位变长/前导补零均正确切分 */
    private static final java.util.regex.Pattern SEQ_TEXT_PATTERN = java.util.regex.Pattern.compile("^0*\\d+");

    /** 回收一个断号入池（REQUIRES_NEW 小事务：同号重复回收只回滚小事务，不毒化外层 doDelete 事务） */
    private void insertGap(String seqKey, long no) {
        try {
            failSafeTxTemplate.execute(status -> {
                new JdbcTemplate(txDataSource).update(
                        "INSERT INTO " + GAP_TABLE + " (seq_key, seq_no) VALUES (?, ?)", seqKey, no);
                return null;
            });
            log.info("[NumberRule] 断号回收: seqKey={} no={}", seqKey, no);
        } catch (Exception e) {
            log.debug("[NumberRule] 断号回收跳过（同号已在池中） seqKey={} no={}", seqKey, no);
        }
    }


    private String renderSegment(JSONObject seg, String type, JSONObject data) {
        if (type == null) return "";
        switch (type) {
            case "constant": {
                String v = seg.getString("value");
                return v != null ? v : "";
            }
            case "random": {
                Integer lenObj = seg.getInteger("length");
                int len = lenObj != null && lenObj > 0 ? Math.min(lenObj, 32) : 8;
                String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
                StringBuilder sb = new StringBuilder(len);
                for (int i = 0; i < len; i++) {
                    sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
                }
                return sb.toString();
            }
            case "sysDate":
                return formatDate(LocalDate.now(), seg.getString("dateFormat"));
            case "bizDate": {
                Object v = getValueIgnoreCase(data, seg.getString("fieldName"));
                LocalDate d = parseDate(v);
                if (d == null) {
                    log.warn("[NumberRule] bizDate 段取值为空或不可解析: field={}", seg.getString("fieldName"));
                    return "";
                }
                return formatDate(d, seg.getString("dateFormat"));
            }
            case "bizField": {
                Object v = getValueIgnoreCase(data, seg.getString("fieldName"));
                String s = v == null ? "" : String.valueOf(v).trim();
                String mode = seg.getString("usageMode");
                Integer take = seg.getInteger("takeLength");
                if (take != null && take > 0 && s.length() > take) {
                    if ("suffix".equals(mode)) return s.substring(s.length() - take);
                    if ("prefix".equals(mode)) return s.substring(0, take);
                }
                return s;
            }
            default:
                return "";
        }
    }

    private String formatDate(LocalDate d, String fmt) {
        if (fmt == null || fmt.isEmpty()) fmt = "yyyyMMdd";
        switch (fmt) {
            case "yyyy-MM-dd":
                return d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "yyyyMM":
                return d.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "yyyy":
                return d.format(DateTimeFormatter.ofPattern("yyyy"));
            default:
                return d.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
    }

    private LocalDate parseDate(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s.substring(0, Math.min(10, s.length())));
        } catch (Exception ignore) {
            // fallthrough
        }
        try {
            return LocalDateTime.parse(s).toLocalDate();
        } catch (Exception ignore) {
            return null;
        }
    }

    private Object getValueIgnoreCase(JSONObject data, String fieldName) {
        if (data == null || fieldName == null || fieldName.isEmpty()) return null;
        Object v = data.get(fieldName);
        if (v == null) v = data.get(fieldName.toUpperCase());
        if (v == null) v = data.get(fieldName.toLowerCase());
        return v;
    }

    /** 流水号段：优先从断号回收池取最小号；池空走发号表自增；含补零/升位开关处理。
     *  seqIdx 为该段在规则中的流水号序号（1 起）；>1 时发号键追加段索引区分多流水号段，=1 保持旧键格式向后兼容 */
    private String nextSequence(JSONObject seg, String bizCode, String fieldName, String prefix, int seqIdx) {
        Integer lenObj = seg.getInteger("seqLength");
        int len = lenObj != null && lenObj > 0 ? Math.min(lenObj, 20) : 4;
        String scope = seg.getString("seqScope");
        Integer startObj = seg.getInteger("seqStart");
        int start = startObj != null && startObj > 0 ? startObj : 1;
        Integer stepObj = seg.getInteger("seqStep");
        int step = stepObj != null && stepObj > 0 ? stepObj : 1;
        Boolean zeroPadObj = seg.getBoolean("seqZeroPad");
        boolean zeroPad = zeroPadObj == null || zeroPadObj;
        Boolean autoExpandObj = seg.getBoolean("seqAutoExpand");
        boolean autoExpand = autoExpandObj != null && autoExpandObj;
        Boolean gapFillObj = seg.getBoolean("seqGapFill");
        boolean gapFill = gapFillObj != null && gapFillObj;

        String scopeKey = scopeKeyOf(scope, LocalDate.now());
        String seqKey = bizCode + ":" + fieldName + ":" + prefix + ":" + scopeKey;
        if (seqIdx > 1) seqKey += ":" + seqIdx;

        Long no = gapFill ? takeFromGapPool(seqKey) : null;
        if (no == null) {
            no = nextNo(seqKey, start, step);
        }

        // 超过长度自动升位：关闭时超长必须报错（截断会重号），开启时自然增长不截断
        if (!autoExpand && String.valueOf(no).length() > len) {
            throw new BizException("CODE_SEQ_OVERFLOW",
                    "编码流水号已达最大长度 " + len + "（当前 " + no + "），请开启「超过长度自动升位」或重置流水号");
        }
        String s = String.valueOf(no);
        if (zeroPad && s.length() < len) {
            s = String.format("%0" + len + "d", no);
        }
        return s;
    }

    /**
     * 从断号回收池取最小号（并发安全）。
     *
     * <p>短事务内 SELECT MIN → DELETE 抢占：并发时两事务可能 MIN 到同一号，
     * 先 DELETE 者获行锁抢到，后 DELETE 者等锁释放后影响行数=0 → 返回 null 走 nextNo 自增。
     * 任何路径都不会两请求拿到同一号。</p>
     */
    private Long takeFromGapPool(String seqKey) {
        ensureSeqTable();
        try {
            return txTemplate.execute(status -> {
                JdbcTemplate jdbc = new JdbcTemplate(txDataSource);
                List<Long> mins = jdbc.queryForList(
                        "SELECT MIN(seq_no) FROM " + GAP_TABLE + " WHERE seq_key = ?", Long.class, seqKey);
                if (mins == null || mins.isEmpty() || mins.get(0) == null) {
                    return null;
                }
                long no = mins.get(0);
                int taken = jdbc.update(
                        "DELETE FROM " + GAP_TABLE + " WHERE seq_key = ? AND seq_no = ?", seqKey, no);
                return taken > 0 ? (Long) no : null;
            });
        } catch (Exception e) {
            log.warn("[NumberRule] 断号池取号失败 seqKey={}: {}", seqKey, e.getMessage());
            return null;
        }
    }

    /**
     * 取下一流水号（并发安全）。
     *
     * <p>① 首行初始化：REQUIRES_NEW 独立小事务 INSERT（撞主键只回滚小事务，不毒化外层业务事务）；
     * ② 发号：REQUIRED 加入外层事务，SELECT ... FOR UPDATE 行锁 → Java 计算 → UPDATE，
     * 锁持到业务事务提交。并发请求在行锁上严格串行，拿到的号不可能相同
     * （PG/Oracle/SQLServer/MySQL/达梦/金仓通用语法；SQLite 库级锁天然串行）。</p>
     *
     * <p>业务回滚时号已消耗（跳号，由断号补偿兜底），但绝不重号。</p>
     */
    private long nextNo(String seqKey, int start, int step) {
        ensureSeqTable();
        ensureSeqRow(seqKey, (long) start - step);
        try {
            Long result = txTemplate.execute(status -> {
                JdbcTemplate jdbc = new JdbcTemplate(txDataSource);
                Long current = jdbc.queryForObject(
                        "SELECT current_no FROM " + SEQ_TABLE + " WHERE seq_key = ? FOR UPDATE",
                        Long.class, seqKey);
                long base = current != null ? current : (long) start - step;
                long next = base + step;
                jdbc.update("UPDATE " + SEQ_TABLE + " SET current_no = ? WHERE seq_key = ?", next, seqKey);
                return next;
            });
            if (result != null) return result;
        } catch (Exception e) {
            log.error("[NumberRule] 发号失败 seqKey={}: {}", seqKey, e.getMessage(), e);
        }
        return start;
    }

    /** 首行初始化：REQUIRES_NEW 小事务 INSERT，行已存在（并发首号/重复发号）时仅回滚小事务 */
    private void ensureSeqRow(String seqKey, long initValue) {
        try {
            failSafeTxTemplate.execute(status -> {
                new JdbcTemplate(txDataSource).update(
                        "INSERT INTO " + SEQ_TABLE + " (seq_key, current_no) VALUES (?, ?)", seqKey, initValue);
                return null;
            });
        } catch (Exception ignore) {
            // 行已存在，正常路径
        }
    }

    /** 懒建发号表与回收池表（存在则跳过；并发建表冲突由 catch 兜底下次重试）。
     *  REQUIRES_NEW 小事务执行 DDL，避免失败毒化外层业务事务。 */
    private synchronized void ensureSeqTable() {
        if (seqTableChecked) return;
        try {
            failSafeTxTemplate.execute(status -> {
                JdbcTemplate jdbc = new JdbcTemplate(txDataSource);
                SqlDialect dialect = dialectRegistry.dialect(DB_NAME);
                if (jdbc.queryForList(dialect.checkTableExistsSql(), Integer.class, SEQ_TABLE).isEmpty()) {
                    jdbc.execute("CREATE TABLE " + SEQ_TABLE + " ("
                            + "seq_key VARCHAR(512) PRIMARY KEY, "
                            + "current_no BIGINT DEFAULT 0, "
                            + "created_at VARCHAR(64))");
                    log.info("[NumberRule] 已创建发号表 {}", SEQ_TABLE);
                }
                if (jdbc.queryForList(dialect.checkTableExistsSql(), Integer.class, GAP_TABLE).isEmpty()) {
                    jdbc.execute("CREATE TABLE " + GAP_TABLE + " ("
                            + "seq_key VARCHAR(512) NOT NULL, "
                            + "seq_no BIGINT NOT NULL, "
                            + "created_at VARCHAR(64), "
                            + "PRIMARY KEY (seq_key, seq_no))");
                    log.info("[NumberRule] 已创建断号回收池 {}", GAP_TABLE);
                }
                return null;
            });
            seqTableChecked = true;
        } catch (Exception e) {
            log.error("[NumberRule] 发号表检查/创建失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 工具方法 ====================


    private int safeLen(String s) {
        return s == null ? 0 : s.length();
    }

    private int dateFmtLen(String fmt) {
        if (fmt == null || fmt.isEmpty()) return 8;
        switch (fmt) {
            case "yyyy-MM-dd": return 10;
            case "yyyyMM": return 6;
            case "yyyy": return 4;
            default: return 8;
        }
    }

    private String scopeKeyOf(String scope, LocalDate d) {
        if ("MONTHLY".equals(scope)) {
            return d.format(DateTimeFormatter.ofPattern("yyyyMM"));
        } else if ("YEARLY".equals(scope)) {
            return d.format(DateTimeFormatter.ofPattern("yyyy"));
        } else if ("GLOBAL".equals(scope)) {
            return "G";
        }
        return d.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /** 从编码中切出的 sysDate 值恢复 scope 日期；无则当前日期 */
    private LocalDate parseScopeDate(String scopeDateStr, String scope) {
        if (scopeDateStr != null) {
            try {
                if (scopeDateStr.length() == 8) return LocalDate.parse(scopeDateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
                if (scopeDateStr.length() == 6) return LocalDate.parse(scopeDateStr + "01", DateTimeFormatter.ofPattern("yyyyMMdd"));
                if (scopeDateStr.length() == 4) return LocalDate.parse(scopeDateStr + "0101", DateTimeFormatter.ofPattern("yyyyMMdd"));
            } catch (Exception ignore) {
                // fallthrough
            }
        }
        return LocalDate.now();
    }

}