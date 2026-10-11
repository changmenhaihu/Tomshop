package com.tom.tomshop;

import com.tom.tomshop.common.constant.CommonConstants;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * TomShop 应用启动类。
 *
 * <p>分层约定：Controller → Service → Mapper，Controller 只做参数校验与转发，
 * 业务逻辑一律收敛在 Service 层。
 *
 * @author Tom
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.tom.tomshop.mapper")
public class TomShopApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(TomShopApplication.class, args);
        Environment environment = context.getEnvironment();

        String port = environment.getProperty("server.port", "8080");
        String profiles = String.join(",", environment.getActiveProfiles());

        log.info("""

                        ----------------------------------------------------------------------
                          TomShop 启动成功
                          应用名称 : {}
                          激活环境 : {}
                          本地端口 : {}
                          接口前缀 : {}
                          接口文档 : http://localhost:{}/doc.html
                          健康探活 : http://localhost:{}{}/health
                        ----------------------------------------------------------------------""",
                environment.getProperty("spring.application.name"),
                profiles.isEmpty() ? "default" : profiles,
                port,
                CommonConstants.API_PREFIX,
                port,
                port, CommonConstants.API_PREFIX);
    }
}
