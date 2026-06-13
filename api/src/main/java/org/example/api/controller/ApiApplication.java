package org.example.api.controller;

import org.springframework.context.annotation.Configuration;

/**
 * API 模块的配置类。
 * 生产环境由 starter 模块的 ApplicationStarter 统一引导，此处不声明 @SpringBootApplication
 * 以避免自动配置被重复触发。
 */
@Configuration
public class ApiApplication {

}
