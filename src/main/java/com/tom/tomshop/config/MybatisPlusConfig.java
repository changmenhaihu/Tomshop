package com.tom.tomshop.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p><b>插件顺序不可随意调整</b>：MyBatis-Plus 官方要求「多租户/动态表名 → 分页 → 乐观锁 →
 * 防全表更新」的顺序，插件按加入顺序形成责任链，顺序错误会导致分页参数丢失或乐观锁不生效。
 *
 * @author Tom
 */
@Configuration
public class MybatisPlusConfig {

    /** 单页最大条数兜底：即使调用方绕过 DTO 校验（编程式调用、内部接口），也不会拖垮数据库 */
    private static final long MAX_PAGE_LIMIT = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 分页插件
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 超过 maxLimit 时自动截断为该值
        pagination.setMaxLimit(MAX_PAGE_LIMIT);
        // 页码越界时返回空列表，而不是回到首页——避免前端「翻到底又跳回第一页」的诡异交互
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // 2. 乐观锁插件：配合 BaseEntity#version 与 @Version，库存扣减等并发场景依赖它兜底
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 3. 防全表更新/删除：拦截没有 where 条件的 update/delete，线上事故的最后一道闸
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
