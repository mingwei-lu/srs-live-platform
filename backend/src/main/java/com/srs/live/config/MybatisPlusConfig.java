package com.srs.live.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.srs.live.common.util.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public IdentifierGenerator identifierGenerator(SnowflakeIdGenerator snowflakeIdGenerator) {
        return entity -> snowflakeIdGenerator.nextId();
    }
}