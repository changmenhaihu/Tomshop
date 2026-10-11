package com.tom.tomshop.convert;

import com.tom.tomshop.common.enums.HealthStatus;
import com.tom.tomshop.dto.HealthDTO;
import com.tom.tomshop.vo.HealthVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 健康检查 DTO ↔ VO 转换器。
 *
 * <p>本转换器是 MapStruct 的用法样板，供后续模块参照：
 * <ul>
 *   <li>同名字段（applicationName、javaVersion 等）自动映射，无需声明。</li>
 *   <li>派生字段用 {@code expression} 调用本接口的 {@code default} 方法，
 *       把组合判断逻辑收在转换器内，Service 不必感知展示层的归纳规则。</li>
 *   <li>{@code LocalDateTime} 未做 {@code dateFormat} 转换——时间格式由全局
 *       {@code JacksonConfig} 在序列化时统一处理，转换器再格式化一次会造成双重职责。</li>
 * </ul>
 *
 * @author Tom
 */
@Mapper(config = BaseConverterConfig.class)
public interface HealthConverter {

    /**
     * DTO → VO。
     *
     * <p>{@code source} 是 MapStruct 对单参数方法的默认入参名，{@code expression} 中可直接引用。
     */
    @Mapping(target = "status", expression = "java(resolveStatus(source))")
    HealthVO toVO(HealthDTO source);

    /**
     * 归纳整体健康状态：数据库与 Redis 全部可用才算 UP。
     *
     * <p>判定为 DEGRADED 而非 DOWN 的原因：本项目的读路径依赖两级缓存
     * （Caffeine + Redis，阶段 4 落地），Redis 不可用时降级直查 MySQL 仍可对外提供服务，
     * 只有数据库不可用才是真正的不可用。
     */
    default String resolveStatus(HealthDTO source) {
        return source.isDatabaseAvailable() && source.isRedisAvailable()
                ? HealthStatus.UP.getCode()
                : HealthStatus.DEGRADED.getCode();
    }
}
