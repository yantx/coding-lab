package com.galio.jasypt;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能Jasypt加解密器，支持：
 * 1. 完全包裹的加密值
 * 2. 嵌入式加密片段
 * 3. 多层嵌套加密
 * 4. 自定义加密前缀/后缀
 */
public class SmartJasyptEncryptor implements StringEncryptor {

    private static final int MAX_RECURSION_DEPTH = 5;
    private final Pattern encPattern;

    private final PooledPBEStringEncryptor delegateEncryptor;
    private final String prefix;
    private final String suffix;

    public SmartJasyptEncryptor(String password) {
        this(password, "PBEWithMD5AndDES", "ENC(", ")");
    }

    public SmartJasyptEncryptor(String password, String algorithm) {
        this(password, algorithm, "ENC(", ")");
    }

    public SmartJasyptEncryptor(String password, String algorithm, String prefix, String suffix) {
        this.delegateEncryptor = createEncryptor(password, algorithm);
        this.prefix = prefix;
        this.suffix = suffix;

        // 构建正则表达式（处理特殊字符转义）
        String patternPrefix = Pattern.quote(prefix);
        String patternSuffix = Pattern.quote(suffix);
        this.encPattern = Pattern.compile(patternPrefix + "([^)]+)" + patternSuffix);
    }

    @Override
    public String encrypt(String message) {
        if (!StringUtils.hasText(message)) {
            return message;
        }
        return wrapEncrypted(delegateEncryptor.encrypt(message));
    }

    @Override
    public String decrypt(String encryptedMessage) {
        if (!StringUtils.hasText(encryptedMessage) || !containsEncryptedFragments(encryptedMessage)) {
            return encryptedMessage;
        }
        return deepDecrypt(encryptedMessage, 0);
    }

    /**
     * 检查字符串是否包含任何加密片段（快速检查）
     */
    public boolean containsEncryptedFragments(String input) {
        return input != null && input.contains(prefix);
    }

    /**
     * 检查整个字符串是否被完全包裹
     */
    public boolean isFullyWrapped(String input) {
        if (input == null) return false;
        return input.startsWith(prefix) &&
                input.endsWith(suffix) &&
                input.indexOf(suffix, prefix.length()) == input.length() - suffix.length();
    }

    /**
     * 判断字符串是否加密（包含任何加密片段）
     */
    public boolean isEncrypted(String input) {
        return containsEncryptedFragments(input);
    }

    /**
     * 包装加密字符串
     */
    public String wrapEncrypted(String encryptedMessage) {
        if (encryptedMessage == null) return null;
        if (isFullyWrapped(encryptedMessage)) {
            return encryptedMessage;
        }
        return prefix + encryptedMessage + suffix;
    }

    /**
     * 递归解密（带深度控制）
     */
    private String deepDecrypt(String input, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return input; // 防止无限递归
        }

        // 处理完全包裹的加密值
        if (isFullyWrapped(input)) {
            String inner = input.substring(prefix.length(), input.length() - suffix.length());
            String decrypted = delegateEncryptor.decrypt(inner);
            return deepDecrypt(decrypted, depth + 1);
        }

        // 处理嵌入式加密内容
        return decryptEmbeddedContent(input, depth);
    }

    /**
     * 处理嵌入式加密内容
     */
    private String decryptEmbeddedContent(String input, int depth) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = encPattern.matcher(input);
        boolean found = false;

        while (matcher.find()) {
            found = true;
            String encryptedContent = matcher.group(1);
            String decrypted;
            try {
                decrypted = delegateEncryptor.decrypt(encryptedContent);
            } catch (Exception e) {
                // 解密失败时保留原始加密内容
                decrypted = matcher.group(0);
            }
            // 递归处理解密后的内容
            decrypted = deepDecrypt(decrypted, depth + 1);
            matcher.appendReplacement(result, Matcher.quoteReplacement(decrypted));
        }

        if (found) {
            matcher.appendTail(result);
            // 递归检查替换后的结果（可能产生新的加密片段）
            return deepDecrypt(result.toString(), depth + 1);
        }

        return input;
    }

    /**
     * 创建底层加密器
     */
    private PooledPBEStringEncryptor createEncryptor(String password, String algorithm) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();

        config.setPassword(password);
        config.setAlgorithm(algorithm);
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.NoIvGenerator");
        config.setStringOutputType("base64");

        encryptor.setConfig(config);
        return encryptor;
    }
}