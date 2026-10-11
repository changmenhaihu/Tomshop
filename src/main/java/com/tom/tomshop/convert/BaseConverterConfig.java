package com.tom.tomshop.convert;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * 所有 MapStruct 转换器的公共配置基座。
 *
 * <p>用法：每个转换器都通过 {@code @Mapper(config = BaseConverterConfig.class)} 继承本配置，
 * 不要在各自的 {@code @Mapper} 上重复声明这些属性——分散声明会导致不同模块的转换行为不一致。
 *
 * <p>属性含义：
 * <ul>
 *   <li>{@code componentModel = SPRING}：生成的实现类注册为 Spring Bean，
 *       用构造器注入即可使用（配合 Lombok 的 {@code @RequiredArgsConstructor} 和 {@code final} 字段）。</li>
 *   <li>{@code unmappedTargetPolicy = WARN}：目标对象存在未被映射的字段时在编译期告警。
 *       刻意不设为 IGNORE——那会把「字段名拼错导致静默丢数据」这类 bug 藏起来。
 *       若确实需要忽略，在具体方法上用 {@code @Mapping(target = "xxx", ignore = true)} 显式声明意图。</li>
 *   <li>{@code nullValuePropertyMappingStrategy = IGNORE}：Update 场景（DTO → 已有 Entity）中，
 *       源对象为 null 的字段不覆盖目标值，避免「只想改库存却把商品名清空」。</li>
 * </ul>
 *
 * @author Tom
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.WARN,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface BaseConverterConfig {
}
