package com.tom.tomshop.service;

import com.tom.tomshop.vo.HealthVO;

/**
 * 健康检查服务。
 *
 * <p>分层约定：Service 接口只暴露业务语义，不出现 {@code HttpServletRequest}、
 * {@code Result} 等 Web 层类型——那些是 Controller 的职责。
 *
 * @author Tom
 */
public interface HealthService {

    /**
     * 采集应用与依赖中间件的健康状态。
     *
     * <p>该方法不会抛出异常：任一依赖探测失败都只反映为返回结果中的降级状态，
     * 由调用方（运维探活脚本、容器 liveness/readiness 探针）决定如何处置。
     *
     * @return 健康检查视图
     */
    HealthVO check();
}
