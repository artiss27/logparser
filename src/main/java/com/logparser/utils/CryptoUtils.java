package com.logparser.utils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;

public class CryptoUtils {
    private static final String KEY_FILE = System.getProperty("user.home") + "/.config/LogParser/.key";

    // Получаем или создаём ключ (256 bit)
    public static SecretKey getOrCreateKey() throws Exception {
        File keyFile = new File(KEY_FILE);
        if (keyFile.exists()) {
            byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
            return new SecretKeySpec(keyBytes, "AES");
        } else {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey key = keyGen.generateKey();
            keyFile.getParentFile().mkdirs();
            Files.write(keyFile.toPath(), key.getEncoded());
            keyFile.setReadable(false, false);
            keyFile.setReadable(true, true);
            return key;
        }
    }

    // Шифрование данных
    public static byte[] encrypt(byte[] data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] encrypted = cipher.doFinal(data);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(iv);
        out.write(encrypted);
        return out.toByteArray();
    }

    // Дешифрование данных
    public static byte[] decrypt(byte[] encrypted, SecretKey key) throws Exception {
        byte[] iv = Arrays.copyOfRange(encrypted, 0, 12);
        byte[] cipherText = Arrays.copyOfRange(encrypted, 12, encrypted.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(cipherText);
    }
}