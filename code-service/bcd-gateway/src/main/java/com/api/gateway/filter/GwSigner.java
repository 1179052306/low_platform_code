package com.api.gateway.filter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** 网关签名生成（与 bcd-code 的校验逻辑一致）：HMAC-SHA256("{uid}|{ts}|{path}", secret) */
public final class GwSigner {

    private GwSigner() {
    }

    public static String sign(String uid, long timestamp, String path, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal((uid + "|" + timestamp + "|" + path).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
