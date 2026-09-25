package com.dking.mini_calling.common.exception;

// exception/BusinessException.java
// 处理业务异常
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
