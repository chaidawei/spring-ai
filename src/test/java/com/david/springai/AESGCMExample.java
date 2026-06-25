package com.david.springai;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class AESGCMExample {

    private static final int AES_KEY_SIZE = 256; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes
    private static final int GCM_TAG_LENGTH = 128; // bits

    public static void main(String[] args) throws Exception {
        // 原文
        String plainText = "Hello, AES-GCM!";

        // 1. 生成 AES 密钥
        SecretKey secretKey = generateAESKey();

        // 2. 加密
        byte[] iv = generateIV();
        byte[] cipherText = encrypt(plainText.getBytes("UTF-8"), secretKey, iv);

        // 3. 解密
        byte[] decrypted = decrypt(cipherText, secretKey, iv);
        System.out.println("解密结果: " + new String(decrypted, "UTF-8"));

        // 可选：Base64 打印
        System.out.println("密钥（Base64）: " + Base64.getEncoder().encodeToString(secretKey.getEncoded()));
        System.out.println("IV（Base64）: " + Base64.getEncoder().encodeToString(iv));
        System.out.println("密文（Base64）: " + Base64.getEncoder().encodeToString(cipherText));
    }

    // 生成 AES 密钥
    public static SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(AES_KEY_SIZE);
        return keyGen.generateKey();
    }

    // 生成随机 IV
    public static byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    // 加密
    public static byte[] encrypt(byte[] plaintext, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        return cipher.doFinal(plaintext);
    }

    // 解密
    public static byte[] decrypt(byte[] ciphertext, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(ciphertext);
    }
}