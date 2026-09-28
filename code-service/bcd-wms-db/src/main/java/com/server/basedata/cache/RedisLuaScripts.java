package com.server.basedata.cache;

/**
 * Redis Lua 脚本常量。
 * <p>
 * 将需要在 Redis 端原子执行的多步操作封装为 Lua 脚本常量，避免多次往返带来的并发竞态，
 * 同时减少网络开销。脚本以字符串形式存储，由调用方通过 Redisson 的 eval/evalSha 执行。
 * <ul>
 * <li>{@link #GET_OR_MISS} — 原子 GET，返回带状态码的二元组</li>
 * <li>{@link #COMPARE_AND_SET} — 带版本比较的条件 SET，保证只写入更新版本</li>
 * <li>{@link #CAS_WITH_VERSION} — 从 JSON 字符串中解析版本并做比较后 SET</li>
 * </ul>
 */
public class RedisLuaScripts {

  /**
   * 原子 GET 或未命中脚本。
   * <p>
   * 返回二元组：第一个元素为状态码（2 表示命中，3 表示未命中），第二个元素为值（未命中时为空串）。
   * 用整数状态码替代 nil/空判断，便于客户端区分"未命中"与"值为空"。
   */
  public static final String GET_OR_MISS = "local val = redis.call('GET', KEYS[1])\n" +
      "if val then\n" +
      "    return {2, val}\n" +
      "end\n" +
      "return {3, ''}";

  /**
   * 带版本比较的条件 SET 脚本。
   * <p>
   * 逻辑：
   * <ol>
   * <li>若 key 不存在，直接 SET 新值并返回 1</li>
   * <li>若 key 存在，比较其当前数据版本（HGET 'dv'）与入参版本，仅当入参版本更新时才 SET</li>
   * <li>写入成功返回 1，因版本较旧跳过返回 0</li>
   * </ol>
   * ARGV[1]=新值，ARGV[2]=过期毫秒，ARGV[3]=新版本号。
   */
  public static final String COMPARE_AND_SET = "local val = redis.call('GET', KEYS[1])\n" +
      "if not val then\n" +
      "    redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])\n" +
      "    return 1\n" +
      "end\n" +
      "local currentVersion = redis.call('HGET', val, 'dv')\n" +
      "if currentVersion and tonumber(currentVersion) >= tonumber(ARGV[3]) then\n" +
      "    return 0\n" +
      "end\n" +
      "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])\n" +
      "return 1";

  /**
   * 从 JSON 字符串中解析版本并做比较后 SET 的脚本。
   * <p>
   * 与 {@link #COMPARE_AND_SET} 类似，但版本信息内嵌在 JSON 字符串的 {@code "_dv":} 字段中，
   * 通过字符串查找定位版本数字，避免在 Redis 端做完整 JSON 解析。
   * <p>
   * 逻辑：
   * <ol>
   * <li>若 key 存在且 JSON 中包含 {@code "_dv":} 字段，解析出版本号</li>
   * <li>当现有版本 ≥ 入参版本时跳过写入，返回 0</li>
   * <li>否则 SET 新值并返回 1</li>
   * </ol>
   * ARGV[1]=新值（JSON），ARGV[2]=新版本号，ARGV[3]=过期毫秒。
   */
  public static final String CAS_WITH_VERSION = "local val = redis.call('GET', KEYS[1])\n" +
      "if val then\n" +
      "    local pos = string.find(val, '\"_dv\":')\n" +
      "    if pos then\n" +
      "        local start = pos + 6\n" +
      "        local endPos = string.find(val, ',', start)\n" +
      "        if not endPos then\n" +
      "            endPos = string.find(val, '}', start)\n" +
      "        end\n" +
      "        if endPos then\n" +
      "            local existingVersion = tonumber(string.sub(val, start, endPos - 1))\n" +
      "            if existingVersion and existingVersion >= tonumber(ARGV[2]) then\n" +
      "                return 0\n" +
      "            end\n" +
      "        end\n" +
      "    end\n" +
      "end\n" +
      "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])\n" +
      "return 1";
}
