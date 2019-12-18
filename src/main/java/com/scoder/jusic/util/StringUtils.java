package com.scoder.jusic.util;

import java.io.UnsupportedEncodingException;

/**
 * String utils
 *
 * @author H
 */
public class StringUtils {

    public static final String ENCODE_GBK = "GBK";
    public static final String ENCODE_UTF_8 = "UTF-8";

    /**
     * 根据给定编码方式获取长度
     *
     * @param str    字符串
     * @param encode 给定编码方式
     * @return 长度
     * @throws UnsupportedEncodingException 异常
     */
    public static int getLength(String str, String encode) throws UnsupportedEncodingException {
        return str.getBytes(encode).length;
    }

    public static int getLength(String s) {
        int length = 0;
        for (int i = 0; i < s.length(); i++) {
            int ascii = Character.codePointAt(s, i);
            if (ascii >= 0 && ascii <= 255) {
                length++;
            } else {
                length += 2;
            }
        }
        return length;
    }

    /**
     * ipv4 脱敏处理
     *
     * @param ipv4 待脱敏的 ip
     * @return 127.0.*.*
     */
    public static String desensitizeIPV4(String ipv4) {
        String[] split = ipv4.split("\\.");
        StringBuilder ip = new StringBuilder();
        for (int i = 0; i < split.length; i++) {
            if (i >= split.length / 2) {
                ip.append("*");
            } else {
                ip.append(split[i]);
            }
            if (i != split.length - 1) {
                ip.append(".");
            }
        }
        return ip.toString();
    }

    public static void main(String[] args) throws UnsupportedEncodingException {

    }

}
