package com.galio.jasypt;

public class SmartEncryptorDemo {

 public static void main(String[] args) {
  // 1. 创建加密器（使用默认设置）
  SmartJasyptEncryptor encryptor = new SmartJasyptEncryptor("mySecretPassword");

  // 2. 加密原始值
  String originalValue = "sensitive-data-123";
  String encryptedValue = encryptor.encrypt(originalValue);
  System.out.println("加密结果: " + encryptedValue);

  // 3. 测试各种解密场景
  testDecryption(encryptor, encryptedValue);

  // 4. 测试自定义前缀/后缀
  testCustomWrapper();
 }

 private static void testDecryption(SmartJasyptEncryptor encryptor, String encryptedValue) {
  // 场景1: 完全包裹的加密值
  String test1 = "ENC(" + encryptedValue + ")";
  System.out.println("\n测试1 (完全包裹):");
  System.out.println("原始: " + test1);
  System.out.println("解密: " + encryptor.decrypt(test1));

  // 场景2: 嵌入式加密片段
  String test2 = "jdbc:mysql://user:ENC(" + encryptedValue + ")@localhost:3306/db";
  System.out.println("\n测试2 (嵌入式):");
  System.out.println("原始: " + test2);
  System.out.println("解密: " + encryptor.decrypt(test2));

  // 场景3: 多个加密片段
  String test3 = "key1=ENC(" + encryptedValue + "), key2=ENC(" + encryptor.encrypt("another-secret") + ")";
  System.out.println("\n测试3 (多个片段):");
  System.out.println("原始: " + test3);
  System.out.println("解密: " + encryptor.decrypt(test3));

  // 场景4: 多层嵌套加密
  String doubleEncrypted = encryptor.wrapEncrypted(encryptor.wrapEncrypted(encryptedValue));
  String test4 = "nested=" + doubleEncrypted;
  System.out.println("\n测试4 (多层嵌套):");
  System.out.println("原始: " + test4);
  System.out.println("解密: " + encryptor.decrypt(test4));

  // 场景5: 未加密内容
  String test5 = "plain-text-value";
  System.out.println("\n测试5 (未加密):");
  System.out.println("原始: " + test5);
  System.out.println("解密: " + encryptor.decrypt(test5));

  // 场景6: 混合内容
  String test6 = "config={user: 'admin', password: ENC(" + encryptedValue + ")}";
  System.out.println("\n测试6 (混合内容):");
  System.out.println("原始: " + test6);
  System.out.println("解密: " + encryptor.decrypt(test6));
 }

 private static void testCustomWrapper() {
  // 使用自定义包装符
  SmartJasyptEncryptor customEncryptor = new SmartJasyptEncryptor(
          "secret123", "PBEWithMD5AndDES", "[", "]");

  String original = "custom-wrapped-value";
  String encrypted = customEncryptor.encrypt(original);
  String test = "connection: user@[password:" + encrypted + "]";

  System.out.println("\n测试自定义包装符:");
  System.out.println("原始: " + test);
  System.out.println("解密: " + customEncryptor.decrypt(test));
 }
}