package com.pptagent.infrastructure.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.pptagent.domain.**.repository")
public class MybatisPlusConfiguration {
}
