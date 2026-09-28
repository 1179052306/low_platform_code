package com.api.lowcode;

import com.server.db.DbConfig;
import com.server.db.MultiDataSource;
import com.server.db.MultiDataSourceHolder;
import com.server.sqlengine.validator.SqlFieldValidator;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 低代码平台元数据表初始化：启动时从 db/lowcode_tables.sql 读取 DDL 并执行（IF NOT EXISTS）。
 *
 * <p>
 * SqlEngineFacade 仅支持 DML/SELECT，DDL 需通过 JdbcTemplate 执行。
 * </p>
 */
@Slf4j
@Component
@Order(1)
public class LowcodeTableInitializer implements CommandLineRunner {

  @Resource(name = "multiDataSource")
  private DataSource dataSource;

  @Resource
  private DbConfig dbConfig;

  @Resource
  private MultiDataSource multiDataSource;

  @Resource
  private SqlFieldValidator sqlFieldValidator;

  @Override
  public void run(String... args) throws Exception {
    // 诊断：打印 resolvedDataSources 的 key
    try {
      Map<Object, DataSource> resolved = multiDataSource.getResolvedDataSources();
      log.info("[LowcodeInit] resolvedDataSources keys: {}", resolved.keySet());
      for (Map.Entry<Object, DataSource> entry : resolved.entrySet()) {
        if (entry.getValue() instanceof HikariDataSource) {
          HikariDataSource hikari = (HikariDataSource) entry.getValue();
          log.info("[LowcodeInit] key={}, poolName={}, jdbcUrl={}", entry.getKey(), hikari.getPoolName(),
              hikari.getJdbcUrl());
        }
      }
      HikariDataSource ds = dbConfig.getHikariDataSource("lowcode");
      log.info("[LowcodeInit] getHikariDataSource(\"lowcode\") = {}", ds != null ? ds.getPoolName() : "null");
    } catch (Exception e) {
      log.warn("[LowcodeInit] 诊断日志异常: {}", e.getMessage());
    }

    MultiDataSourceHolder.setDatasource("lowcode");
    try {
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      try (InputStream is = getClass().getClassLoader().getResourceAsStream("db/lowcode_tables.sql");
          BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (trimmed.isEmpty() || trimmed.startsWith("--")) {
            continue;
          }
          sb.append(line).append("\n");
          if (trimmed.endsWith(";")) {
            String stmt = sb.toString().trim();
            stmt = stmt.substring(0, stmt.length() - 1).trim();
            if (!stmt.isEmpty()) {
              log.info("执行DDL: {}", stmt.substring(0, Math.min(80, stmt.length())));
              jdbc.execute(stmt);
            }
            sb.setLength(0);
          }
        }
      }
      log.info("低代码平台元数据表初始化完成");

      // 建表后清除 SqlFieldValidator 元数据缓存，确保下次查询重新加载
      sqlFieldValidator.evictAllCache();
      log.info("[LowcodeInit] SqlFieldValidator 元数据缓存已清除");
    } finally {
      MultiDataSourceHolder.clearDataSource();
    }
  }
}
