package com.galio.jasypt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Jasypt配置属性
 */
@Data
@ConfigurationProperties(prefix = "jasypt.encryptor")
public class JasyptProperties {
    
    /**
     * 加密密钥
     */
    private String password = "test_password";
    
    /**
     * 加密算法，默认：PBEWithMD5AndDES
     */
    private String algorithm = "PBEWithMD5AndDES";
    
    /**
     * 密钥获取次数，默认：1000
     */
    private int keyObtentionIterations = 1000;
    
    /**
     * 加密池大小，默认：1
     */
    private int poolSize = 1;
    
    /**
     * 加密器名称，默认：smartJasyptEncryptor
     */
    private String bean = "smartJasyptEncryptor";
    
    /**
     * 加密前缀，默认：ENC(
     */
    private String prefix = "ENC(";
    
    /**
     * 加密后缀，默认：)
     */
    private String suffix = ")";
    
    /**
     * 加密模式，默认：ECB
     */
    private String mode = "ECB";
    
    /**
     * 盐值生成器类名
     */
    private String saltGeneratorClassname = "org.jasypt.salt.RandomSaltGenerator";
    
    /**
     * IV生成器类名
     */
    private String ivGeneratorClassname = "org.jasypt.iv.NoIvGenerator";
    
    /**
     * 字符串输出类型，默认：base64
     */
    private String stringOutputType = "base64";
}
