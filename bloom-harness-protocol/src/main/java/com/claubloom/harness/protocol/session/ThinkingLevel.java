package com.claubloom.harness.protocol.session;

import com.claubloom.harness.protocol.codec.ProtocolValidationError;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 推理模型的思考级别选项。
 */
public enum ThinkingLevel {
    OFF("off"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    private final String value;

    ThinkingLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ThinkingLevel fromValue(String value) {
        if (value == null) {
            return OFF;
        }
        for (ThinkingLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        return OFF;
    }

    /**
     * 严格解析思考级别：与宽容的 {@link #fromValue(String)}（{@code @JsonCreator} 使用，
     * 未知取值回退 {@link #OFF}，以保证协议前向兼容）不同，本方法对未知取值抛出
     * {@link ProtocolValidationError}，使 REST 层能够返回 400 并带上可读提示，
     * 而不是把用户的拼写错误静默吞掉。
     *
     * @param value 取值字符串（大小写不敏感）
     * @return 匹配的思考级别；{@code value} 为 {@code null} 时返回 {@code null}
     * @throws ProtocolValidationError 当取值不是任何已知级别时
     */
    public static ThinkingLevel parse(String value) {
        if (value == null) {
            return null;
        }
        for (ThinkingLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        throw new ProtocolValidationError(
                "无法识别的思考级别 '" + value + "'，可选值："
                        + Stream.of(values()).map(ThinkingLevel::getValue).collect(Collectors.joining("、")));
    }
}
