package com.server.encryption;

import java.security.MessageDigest;

public class Encryption {

    // 16进制下数字到字符的映射数组
    private static String[] hexDigits = new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c",
            "d", "e", "f" };

    // 将inputstr加密的方法
    public static String createPassword(String inputstr) {
        return encodeByMD5(inputstr);
    }

    // 验证密码是否正确
    public static boolean authenticatePassword(String pass, String inputstr) {
        if (pass.equals((encodeByMD5(inputstr)))) {
            return true;
        } else {
            return false;
        }
    }

    // 对字符串进行MD5编码
    private static String encodeByMD5(String originstr) {
        if (originstr != null) {
            try {
                // 创建具有指定算法名称的信息摘要
                MessageDigest md = MessageDigest.getInstance("MD5");
                // 使用指定的字节数组对摘要进行最后的更新，然后完成摘要计算
                byte[] results = md.digest(originstr.getBytes());
                // 将得到的字节数组编程字符串返回
                String resultString = byteArrayToHex(results);
//                String resultString = byteArrayToHexString(results);
                return resultString.toLowerCase();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    // 转换字节数组为十六进制字符串
    private static String byteArrayToHexString(byte[] b) {
        StringBuffer resultsb = new StringBuffer();
        int i = 0;
        for (i = 0; i < b.length; i++) {
            resultsb.append(byteToHexString(b[i]));
        }
        return resultsb.toString();
    }

    // 将字节转化成十六进制的字符串
    private static String byteToHexString(byte b) {
        int n = b;
        if (n < 0) {
            n = 256 + n;
        }
        int d1 = n / 16;
        int d2 = n / 16;
        return hexDigits[d1] + hexDigits[d2];
    }

    /**
     * 第二种加密方式
     *
     * @param byteArray
     * @Return String
     */ 
    private static String byteArrayToHex(byte[] byteArray) {
        char[] hexDigits = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };
        char[] resultCharArray = new char[byteArray.length * 2];
        int index = 0;
        for (byte b : byteArray) {
            resultCharArray[index++] = hexDigits[b>>> 4 & 0xf];
            resultCharArray[index++] = hexDigits[b& 0xf];
        }
        return new String(resultCharArray);
    }

    public static String phoneSecrecy(String phoneNum){
        if(phoneNum != null && phoneNum.length() > 4){
            if(phoneNum.length() == 11){
                phoneNum = phoneNum.substring(0, 3) + "****" + phoneNum.substring(7, phoneNum.length());
            }else{
                phoneNum = phoneNum.substring(0,phoneNum.length()-1) + "*";
            }
        }
        return phoneNum;
    }
    /**
     * 手机号用****号隐藏中间数字
     *
     * @param phone
     * @return String
     */
    public static String settingphone(String phone) {
        String phone_s = phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
        return phone_s;
    }


    /**
     * 邮箱用****号隐藏前面的字母
     *
     * @param email
     * @return String
     */
    public static String settingemail(String email) {
        String emails = email.replaceAll("(\\w?)(\\w+)(\\w)(@\\w+\\.[a-z]+(\\.[a-z]+)?)", "$1****$3$4");
        return emails;
    }
}
