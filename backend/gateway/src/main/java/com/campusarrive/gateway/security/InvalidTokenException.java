package com.campusarrive.gateway.security;

/**
 * 令牌校验异常。
 *
 * <p>规格来源：FR-04-02 — 当 iss/aud 等声明不匹配时抛出，由鉴权过滤器捕获并返回 401。</p>
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
