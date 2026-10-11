package com.tom.tomshop.controller;

import com.tom.tomshop.common.constant.CommonConstants;
import com.tom.tomshop.common.result.Result;
import com.tom.tomshop.service.HealthService;
import com.tom.tomshop.vo.HealthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 *
 * <p>Controller 纪律（全项目生效）：只做「接参 → 转发 → 包装返回值」，
 * 不写任何 if 业务分支、不注入 Mapper、不出现事务注解。
 *
 * @author Tom
 */
@Tag(name = "00. 健康检查", description = "应用与依赖中间件的探活接口")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @Operation(summary = "探活",
            description = "返回应用基本信息，以及 MySQL、Redis 的实时连通状态。该接口免登录。")
    @GetMapping
    public Result<HealthVO> health() {
        return Result.success(healthService.check());
    }
}
