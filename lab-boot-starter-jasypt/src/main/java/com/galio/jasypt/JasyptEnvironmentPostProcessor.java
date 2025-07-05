package com.galio.jasypt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Jasypt环境后置处理器，用于提前解密配置
 */
public class JasyptEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String ENCRYPTED_PROPERTY_SOURCE_NAME = "jasyptEncryptedProperties";
    private static final String SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME = "systemEnvironment";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 创建JasyptUtils实例（只创建一次）
        SmartJasyptEncryptor smartJasyptEncryptor = createStringEncryptor(environment);

        // 获取属性源
        MutablePropertySources propertySources = environment.getPropertySources();

        // 创建一个新的属性源来存储解密后的属性
        Map<String, Object> decryptedProperties = new HashMap<>();

        // 处理系统环境变量
        if (propertySources.contains(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            PropertySource<?> systemEnvPropertySource = propertySources.get(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            if (systemEnvPropertySource instanceof SystemEnvironmentPropertySource) {
                processSystemEnvironment(
                        (SystemEnvironmentPropertySource) systemEnvPropertySource,
                        smartJasyptEncryptor,
                        decryptedProperties
                );
            }
        }

        // 处理其他属性源（按优先级从高到低处理）
        for (PropertySource<?> propertySource : propertySources) {
            if (propertySource instanceof OriginTrackedMapPropertySource) {
                processOriginTrackedMapPropertySource(
                        (OriginTrackedMapPropertySource) propertySource,
                        smartJasyptEncryptor,
                        decryptedProperties,
                        environment
                );
            } else if (propertySource instanceof MapPropertySource) {
                processMapPropertySource(
                        (MapPropertySource) propertySource,
                        smartJasyptEncryptor,
                        decryptedProperties,
                        environment
                );
            }
        }

        // 如果有解密后的属性，添加到环境变量中
        if (!decryptedProperties.isEmpty()) {
            MapPropertySource decryptedPropertySource = new MapPropertySource(
                    ENCRYPTED_PROPERTY_SOURCE_NAME,
                    decryptedProperties
            );
            propertySources.addFirst(decryptedPropertySource);
        }
    }

    private void processSystemEnvironment(SystemEnvironmentPropertySource source,
                                          SmartJasyptEncryptor smartJasyptEncryptor,
                                          Map<String, Object> decryptedProperties) {
        // 系统环境变量总是处理，不检查是否已存在
        String[] propertyNames = source.getPropertyNames();
        for (String propertyName : propertyNames) {
            Object value = source.getProperty(propertyName);
            if (value instanceof String) {
                processValue(propertyName, (String) value, smartJasyptEncryptor, decryptedProperties);
            }
        }
    }

    private void processOriginTrackedMapPropertySource(OriginTrackedMapPropertySource source,
                                                       SmartJasyptEncryptor smartJasyptEncryptor,
                                                       Map<String, Object> decryptedProperties,
                                                       ConfigurableEnvironment environment) {
        source.getSource().forEach((key, value) -> {
            if (value instanceof String && !environment.containsProperty(key)) {
                processValue(key, (String) value, smartJasyptEncryptor, decryptedProperties);
            }
        });
    }

    private void processMapPropertySource(MapPropertySource source,
                                          SmartJasyptEncryptor smartJasyptEncryptor,
                                          Map<String, Object> decryptedProperties,
                                          ConfigurableEnvironment environment) {
        source.getSource().forEach((key, value) -> {
            if (value instanceof String && !environment.containsProperty(key)) {
                processValue(key, (String) value, smartJasyptEncryptor, decryptedProperties);
            }
        });
    }

    private void processValue(String key, String value,
                              SmartJasyptEncryptor smartJasyptEncryptor,
                              Map<String, Object> decryptedProperties) {
        if (isEncrypted(value)) {
            try {
                String decryptedValue = smartJasyptEncryptor.decrypt(value);
                decryptedProperties.put(key, decryptedValue);
            } catch (Exception e) {
                System.err.println("Error decrypting property '" + key + "': " + e.getMessage());
            }
        }
    }

    private boolean isEncrypted(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.startsWith("ENC(") && value.endsWith(")");
    }

    private SmartJasyptEncryptor createStringEncryptor(ConfigurableEnvironment environment) {
        String password = environment.getProperty("jasypt.encryptor.password", "test_password");
        if (!StringUtils.hasText(password)) {
            password = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
        }
        // 初始化JasyptContext
        if (Objects.isNull(JasyptContext.getInstance())) {
            JasyptContext.initialize(
                    password,
                    environment.getProperty("jasypt.encryptor.algorithm"),
                    environment.getProperty("jasypt.encryptor.prefix"),
                    environment.getProperty("jasypt.encryptor.suffix")
            );
        }
        return JasyptContext.getInstance().getStringEncryptor();
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }
}
