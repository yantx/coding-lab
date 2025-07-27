package com.example.eurekaclient;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;

public class PasswordEncryptor {
    public static void main(String[] args) {
        String password = "password"; // 加密密钥
        String value = "password123"; // 要加密的密码
        
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(password);
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setIvGenerator(new RandomIvGenerator());
        
        String encrypted = encryptor.encrypt(value);
        System.out.println("Encrypted password: ENC(" + encrypted + ")");
    }
}
