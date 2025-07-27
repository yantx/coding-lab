# Lab Boot Starter Jasypt

这是一个基于Spring Boot的Jasypt加解密Starter，提供了简单易用的API来加密和解密敏感配置信息。

## 功能特性

- 支持多种加密算法
- 自动配置，开箱即用
- 提供工具类简化加解密操作
- 支持自定义前缀和后缀
- 线程安全的加解密操作

## 快速开始

### 1. 添加依赖

在`pom.xml`中添加以下依赖：

```xml
<dependency>
    <groupId>com.galio</groupId>
    <artifactId>lab-boot-starter-jasypt</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置参数

在`application.yml`或`application.properties`中添加配置：

```yaml
jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD:your-secret-key}  # 加密密钥，建议通过环境变量设置 可选
    algorithm: PBEWithMD5AndDES  # 加密算法，可选
    pool-size: 1  # 加密池大小，可选
    key-obtention-iterations: 1000  # 密钥获取次数，可选
    prefix: "ENC("  # 加密前缀，可选
    suffix: ")"     # 加密后缀，可选
```

### 3. 使用示例

#### 3.1 在配置文件中使用加密值

```yaml
spring:
  datasource:
    username: admin
    password: ENC(加密后的密码)
```

#### 3.2 在代码中使用JasyptUtils

```java
@RestController
public class DemoController {
    
    @Resource
    private JasyptUtils jasyptUtils;
    
    @GetMapping("/encrypt")
    public String encrypt(String text) {
        return jasyptUtils.wrapEncrypted(jasyptUtils.encrypt(text));
    }
    
    @GetMapping("/decrypt")
    public String decrypt(String encryptedText) {
        return jasyptUtils.decrypt(encryptedText);
    }
}
```

### 4. 生成加密值

```java
@SpringBootTest
public class EncryptionTest {

    @Resource
    private StringEncryptor stringEncryptor;
    
    @Resource
    private JasyptUtils jasyptUtils;

    @Test
    public void generateEncryptedValue() {
        String rawPassword = "your-password";
        String encrypted = jasyptUtils.wrapEncrypted(stringEncryptor.encrypt(rawPassword));
        System.out.println("Encrypted: " + encrypted);
    }
}
```

## 配置参数说明

| 参数名 | 描述 | 默认值 |
|--------|------|--------|
| jasypt.encryptor.password | 加密密钥，必须配置 | 无 |
| jasypt.encryptor.algorithm | 加密算法 | PBEWithMD5AndDES |
| jasypt.encryptor.key-obtention-iterations | 密钥获取次数 | 1000 |
| jasypt.encryptor.pool-size | 加密池大小 | 1 |
| jasypt.encryptor.prefix | 加密前缀 | ENC( |
| jasypt.encryptor.suffix | 加密后缀 | ) |
| jasypt.encryptor.string-output-type | 字符串输出类型 | base64 |

## 安全建议

1. 不要将加密密钥直接写在配置文件中，建议通过环境变量或启动参数传入
2. 生产环境使用强密码，长度建议不少于16位
3. 定期更换加密密钥

## 许可证

MIT License
