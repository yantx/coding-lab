package com.lab.eureka;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * @Author:
 * @Date: 2025-04-06 20:52:25
 * @Description:
 */
@Configuration
public class BigDecimalConfig {
    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build();
        // 注册自定义Double序列化器
        SimpleModule doubleModule = new SimpleModule();
        doubleModule.addSerializer(Double.class, new DoubleSerializer());
        mapper.registerModule(doubleModule);

        return mapper;
    }

    // 自定义Double序列化逻辑
    private static class DoubleSerializer extends JsonSerializer<Double> {
        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            // 避免Double转BigDecimal精度丢失，改用字符串构造
            gen.writeString(new BigDecimal(value.toString()).toPlainString());
        }
    }
}
