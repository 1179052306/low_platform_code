package com.server.encryption;


import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import java.util.Base64;


/**
 * @Description: 3DES解密
 * @Author: 张博文
 * @Date: 2023/6/6 10:16
 */

public class DESUtils {

    /**
     * 字符串解密
     *
     * @param encryptedMessage
     * @param key
     * @return String
     * @author 张博文
     * @date: 2023/6/6
     **/
    public static String decryptByDES(String encryptedMessage, String key) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedMessage);
        byte[] keyBytes = key.getBytes("UTF-8");

        DESKeySpec desKeySpec = new DESKeySpec(keyBytes);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        SecretKey secretKey = keyFactory.generateSecret(desKeySpec);

        Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

        return new String(decryptedBytes, "UTF-8");
    }


    /**
     * 字符串加密
     *
     * @param message
     * @param key
     * @return String
     * @author 张博文
     * @date: 2023/6/6
     **/
    public static String encryptByDES(String message, String key) throws Exception {
        byte[] messageBytes = message.getBytes("UTF-8");
        byte[] keyBytes = key.getBytes("UTF-8");

        DESKeySpec desKeySpec = new DESKeySpec(keyBytes);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        SecretKey secretKey = keyFactory.generateSecret(desKeySpec);

        Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] encryptedBytes = cipher.doFinal(messageBytes);

        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

}
