package com.tom.tomshop.common.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 全局统一响应结果。
 *
 * <p>所有 Controller 的返回值必须是 {@code Result<T>}，禁止直接返回实体或 {@code ResponseEntity}。
 * 实例只能通过静态工厂方法创建，避免各处 new 出不一致的 code / message。
 *
 * <p><b>序列化约定</b>：{@code timestamp} 刻意使用基本类型 {@code long}，
 * 因为 JacksonConfig 中将包装类型 {@code Long} 统一序列化为字符串（保护雪花 ID 精度），
 * 基本类型 {@code long} 保持数字语义。详见 {@code JacksonConfig} 类注释。
 *
 * @param <T> 业务数据类型
 * @author Tom
 */
@Data
@Schema(description = "统一响应结果")
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "业务状态码，200 表示成功", example = "200")
    private Integer code;

    @Schema(description = "提示信息", example = "操作成功")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    @Schema(description = "服务端响应时间戳（毫秒）", example = "1735689600000")
    private long timestamp;

    /**
     * 私有构造，强制使用静态工厂。
     * {@code timestamp} 在构造时统一打点，保证同一响应体时间一致。
     */
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // ==================== 成功 ====================

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    // ==================== 失败 ====================

    public static <T> Result<T> fail(String message) {
        return new Result<>(ResultCode.FAILED.getCode(), message, null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /**
     * 判断是否成功。派生属性，不参与 JSON 序列化（避免响应体出现冗余的 success 字段）。
     */
    @JsonIgnore
    public boolean isSuccess() {
        return ResultCode.SUCCESS.getCode().equals(this.code);
    }
}
