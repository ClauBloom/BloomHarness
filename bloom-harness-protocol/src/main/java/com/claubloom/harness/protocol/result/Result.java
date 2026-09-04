package com.claubloom.harness.protocol.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Functional Result container representing either success (Ok) or failure (Err).
 *
 * @param <T> value type
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "status"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Result.Ok.class, name = "ok"),
        @JsonSubTypes.Type(value = Result.Err.class, name = "err")
})
public sealed interface Result<T> permits Result.Ok, Result.Err {

    boolean isOk();

    boolean isErr();

    T getOrNull();

    ErrorInfo getErrorOrNull();

    default T getOrElse(T defaultValue) {
        return isOk() ? getOrNull() : defaultValue;
    }

    default <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        if (this instanceof Ok<T> ok) {
            return ok(mapper.apply(ok.value()));
        } else if (this instanceof Err<T> err) {
            return err(err.error());
        }
        throw new IllegalStateException("Unknown result type");
    }

    default <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
        Objects.requireNonNull(mapper);
        if (this instanceof Ok<T> ok) {
            return mapper.apply(ok.value());
        } else if (this instanceof Err<T> err) {
            return err(err.error());
        }
        throw new IllegalStateException("Unknown result type");
    }

    default Result<T> ifOk(Consumer<? super T> consumer) {
        if (this instanceof Ok<T> ok) {
            consumer.accept(ok.value());
        }
        return this;
    }

    default Result<T> ifErr(Consumer<ErrorInfo> consumer) {
        if (this instanceof Err<T> err) {
            consumer.accept(err.error());
        }
        return this;
    }

    static <T> Result<T> ok(T value) {
        return new Ok<>(value);
    }

    static <T> Result<T> err(ErrorInfo error) {
        return new Err<>(error);
    }

    static <T> Result<T> err(String code, String message) {
        return new Err<>(ErrorInfo.of(code, message));
    }

    record Ok<T>(T value) implements Result<T> {
        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public boolean isErr() {
            return false;
        }

        @Override
        public T getOrNull() {
            return value;
        }

        @Override
        public ErrorInfo getErrorOrNull() {
            return null;
        }
    }

    record Err<T>(ErrorInfo error) implements Result<T> {
        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public boolean isErr() {
            return true;
        }

        @Override
        public T getOrNull() {
            return null;
        }

        @Override
        public ErrorInfo getErrorOrNull() {
            return error;
        }
    }
}
