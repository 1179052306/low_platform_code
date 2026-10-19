package com.api.lowcode.columnconfig;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ColumnConfigService {

    private static final String CONFIG_DIR = "Uploads/column-config";
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final JSONObject config;
        final long createdAt;
        CacheEntry(JSONObject config) {
            this.config = config;
            this.createdAt = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }

    private String cacheKey(String userId, String pageId) {
        return userId + ":" + pageId;
    }

    private Path filePath(String userId, String pageId) {
        return Paths.get(CONFIG_DIR, userId, pageId + ".json");
    }

    public JSONObject getColumnConfig(String userId, String pageId) {
        if (userId == null || userId.isEmpty() || pageId == null || pageId.isEmpty()) {
            return null;
        }
        String key = cacheKey(userId, pageId);
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.config;
        }
        try {
            Path path = filePath(userId, pageId);
            if (Files.exists(path)) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                JSONObject config = JSONObject.parseObject(content);
                cache.put(key, new CacheEntry(config));
                return config;
            }
        } catch (IOException e) {
            log.error("读取列配置失败: userId={}, pageId={}", userId, pageId, e);
        }
        cache.put(key, new CacheEntry(null));
        return null;
    }

    public void saveColumnConfig(String userId, String pageId, JSONObject config) {
        if (userId == null || userId.isEmpty() || pageId == null || pageId.isEmpty()) {
            return;
        }
        try {
            Path path = filePath(userId, pageId);
            Files.createDirectories(path.getParent());
            Files.writeString(path, config.toJSONString(), StandardCharsets.UTF_8);
            cache.put(cacheKey(userId, pageId), new CacheEntry(config));
        } catch (IOException e) {
            log.error("保存列配置失败: userId={}, pageId={}", userId, pageId, e);
        }
    }
}