package com.security.oauth2.config;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.DigestUtils;

import java.util.regex.Pattern;

/**
 * 智能密码编码器
 * 自动识别密码是否已经是MD5值
 */
public class SmartPasswordEncoder implements PasswordEncoder {

    // MD5正则：32位十六进制
    private static final Pattern MD5_PATTERN =
            Pattern.compile("^[a-fA-F0-9]{32}$");

    @Override
    public String encode(CharSequence rawPassword) {
        // 注册新用户时使用
        // 如果传的是MD5值，直接返回
        // 否则进行MD5加密
        if (isMd5(rawPassword.toString())) {
            return rawPassword.toString();
        }
        return DigestUtils.md5DigestAsHex(rawPassword.toString().getBytes());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        // 判断前端传来的密码是否已经是MD5
        String rawStr = rawPassword.toString();

        if (isMd5(rawStr)) {
            // 已经是MD5值，直接比较
            System.out.println("[智能编码器] 前端密码已是MD5，直接比较");
            return encodedPassword.equalsIgnoreCase(rawStr);
        } else {
            // 是明文，需要加密后比较
            System.out.println("[智能编码器] 前端密码是明文，先MD5加密");
            String encrypted = DigestUtils.md5DigestAsHex(rawStr.getBytes());
            return encodedPassword.equalsIgnoreCase(encrypted);
        }
    }

    /**
     * 判断字符串是否是MD5格式
     */
    private boolean isMd5(String str) {
        if (str == null || str.length() != 32) {
            return false;
        }
        return MD5_PATTERN.matcher(str).matches();
    }
}
