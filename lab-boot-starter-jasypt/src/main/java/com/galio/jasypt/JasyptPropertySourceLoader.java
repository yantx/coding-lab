package com.galio.jasypt;

import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

public class JasyptPropertySourceLoader implements PropertySourceLoader, Ordered {

    private static final String[] ENCRYPTED_PROPERTIES = {
            "eureka.client.password",
            "eureka.client.service-url.defaultZone",
            "eureka.client.serviceUrl.defaultZone"
    };

    private final PropertySourceLoader delegate;
    private final SmartJasyptEncryptor smartJasyptEncryptor;

    public JasyptPropertySourceLoader() {
        // 根据文件类型选择合适的加载器
        this.delegate = new YamlPropertySourceLoader(); // 或 new PropertiesPropertySourceLoader()
        this.smartJasyptEncryptor = createStringEncryptor();
    }

    @Override
    public String[] getFileExtensions() {
        return delegate.getFileExtensions();
    }

    @Override
    public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
        // 1. 使用标准加载器加载原始属性
        List<PropertySource<?>> sources = delegate.load(name, resource);
        List<PropertySource<?>> result = new ArrayList<>(sources.size());

        // 2. 处理每个属性源
        for (PropertySource<?> source : sources) {
            if (source.getSource() instanceof Map) {
                // 创建新的可修改的Map来存储解密后的属性
                Map<String, Object> originalMap = (Map<String, Object>) source.getSource();
                Map<String, Object> decryptedMap = new LinkedHashMap<>(originalMap);
                boolean modified = false;

                // 3. 解密关键属性
                for (String property : ENCRYPTED_PROPERTIES) {
                    Object value = decryptedMap.get(property);
                    if (value != null) {
                        try {
                            String encryptedValue = null;

                            // 处理OriginTrackedValue包装的值
                            if (value instanceof OriginTrackedValue) {
                                Object rawValue = ((OriginTrackedValue) value).getValue();
                                if (rawValue instanceof CharSequence) {
                                    encryptedValue = rawValue.toString();
                                }
                            }
                            // 处理普通字符串值
                            else if (value instanceof CharSequence) {
                                encryptedValue = value.toString();
                            }

                            // 如果值是加密的，则解密并更新
                            if (encryptedValue != null && smartJasyptEncryptor.isEncrypted(encryptedValue)) {
                                String decryptedValue = smartJasyptEncryptor.decrypt(encryptedValue);
                                decryptedMap.put(property, decryptedValue);
                                modified = true;

                                // 记录解密日志（可选）
                                System.out.println("Decrypted property: " + property);
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to decrypt property: " + property + ", error: " + e.getMessage());
                            // 可以选择继续处理其他属性
                            continue;
                        }
                    }
                }

                // 4. 创建新的PropertySource，替换原始属性源
                PropertySource<?> newSource = source instanceof OriginTrackedMapPropertySource
                        ? new OriginTrackedMapPropertySource(source.getName(),
                        Collections.unmodifiableMap(decryptedMap), true)
                        : new MapPropertySource(source.getName() + ".decrypted", decryptedMap);

                result.add(newSource);
            } else {
                // 如果不是Map类型的属性源，直接添加
                result.add(source);
            }
        }
        return result;
    }
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // 最高优先级
    }
    private SmartJasyptEncryptor createStringEncryptor() {
        String password = System.getProperty("jasypt.encryptor.password", "test_password");
        if (!StringUtils.hasText(password)) {
            password = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
        }
        // 初始化JasyptContext
        if (Objects.isNull(JasyptContext.getInstance())) {
            JasyptContext.initialize(
                    password,
                    System.getProperty("jasypt.encryptor.algorithm"),
                    System.getProperty("jasypt.encryptor.prefix"),
                    System.getProperty("jasypt.encryptor.suffix")
            );
        }
        return JasyptContext.getInstance().getStringEncryptor();
    }

}