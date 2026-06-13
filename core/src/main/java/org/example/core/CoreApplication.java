package org.example.core;

import org.springframework.context.annotation.Configuration;

/**
 * Core 模块的配置类。
 * 单独测试 core 模块时，请使用 ApplicationStarter 或自行创建测试入口。
 * 生产环境由 starter 模块的 ApplicationStarter 统一引导，此处不声明 @SpringBootApplication
 * 以避免 JPA Repository 被重复扫描注册。
 */
@Configuration
public class CoreApplication {

}
