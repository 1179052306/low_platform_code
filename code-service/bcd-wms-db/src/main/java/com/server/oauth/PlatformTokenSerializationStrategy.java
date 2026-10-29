package com.server.oauth;

import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStoreSerializationStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 平台共享 Token 序列化策略（定向放行白名单版 JDK 序列化）：
 * spring-security-oauth2 2.3.7 默认 JdkSerializationStrategy 的 SaferObjectInputStream
 * 只允许 java.* / org.springframework.* 反序列化，平台自定义类（LowcodeUserDetails）被拒。
 * 本策略在保持防 RCE 的前提下定向放行平台包前缀，auth 与 system 两处 TokenStore 必须使用同一实现。
 */
public class PlatformTokenSerializationStrategy implements RedisTokenStoreSerializationStrategy {

    private static final List<String> ALLOWED_PREFIXES = Arrays.asList(
            "java.", "javax.", "org.springframework.", "com.server.oauth.", "com.alibaba.fastjson2.",
            "[Ljava.", "[Lorg.springframework.", "[Lcom.server.oauth."
    );

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (ObjectInputStream ois = new WhitelistObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) ois.readObject();
        } catch (Exception e) {
            throw new IllegalArgumentException("Token 反序列化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String deserializeString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] serialize(Object object) {
        if (object == null) {
            return new byte[0];
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(512);
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(object);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Token 序列化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] serialize(String string) {
        if (string == null) {
            return new byte[0];
        }
        return string.getBytes(StandardCharsets.UTF_8);
    }

    /** 定向白名单反序列化（防任意类注入，仅放行平台所需包前缀） */
    private static class WhitelistObjectInputStream extends ObjectInputStream {

        WhitelistObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String name = desc.getName();
            for (String prefix : ALLOWED_PREFIXES) {
                if (name.startsWith(prefix)) {
                    return super.resolveClass(desc);
                }
            }
            throw new ClassNotFoundException("不允许反序列化类: " + name);
        }
    }
}