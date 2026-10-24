package com.tom.tomshop.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单号生成工具。
 *
 * <p>订单号是<b>对客展示</b>的业务号，与主键（雪花 ID，19 位）职责不同，因此不复用雪花 ID ——
 * 19 位纯数字对用户和客服都不友好，报单号时容易说错。
 *
 * <p>格式（共 24 位）：
 * <pre>
 *   yyyyMMddHHmmss + 用户ID后4位 + 6位随机数
 *   20261015143012   0001          834721
 *   └── 时间前缀 ──┘ └─ 归属 ─┘    └ 抗碰撞 ┘
 * </pre>
 * 时间前缀让订单号天然按时间有序（客服肉眼即可判断下单时间），用户后缀便于定位归属，
 * 尾部随机数用于抵御同秒并发。
 *
 * <p><b>唯一性如何保证</b>：本类只负责「生成一个候选值」，<b>不保证全局唯一</b>。
 * 调用方需要做一次存在性校验重试，数据库的 {@code uk_order_no} 唯一索引是最终兜底。
 * 分层的理由：生成逻辑是纯函数（便于单测），唯一性涉及数据库，不该混在工具类里。
 *
 * @author Tom
 */
public final class OrderNoGenerator {

    /** 时间前缀格式，长度 14 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 随机后缀上界（6 位，取模用） */
    private static final int RANDOM_BOUND = 1_000_000;

    /** 用户后缀取模基数（保留用户 ID 后 4 位） */
    private static final long USER_MOD = 10_000L;

    private OrderNoGenerator() {
    }

    /**
     * 生成一个订单号候选值。
     *
     * @param userId 下单用户 ID；为 null 时用户后缀补 0000
     * @return 24 位订单号
     */
    public static String generate(Long userId) {
        String time = LocalDateTime.now().format(TIME_FORMATTER);
        long userPart = userId == null ? 0L : Math.floorMod(userId, USER_MOD);
        int randomPart = ThreadLocalRandom.current().nextInt(RANDOM_BOUND);
        return time + String.format("%04d", userPart) + String.format("%06d", randomPart);
    }
}
