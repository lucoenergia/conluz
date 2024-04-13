package org.lucoenergia.conluz.infrastructure.production.huawei;

public class HuaweiException extends RuntimeException {

    public HuaweiException(String message) {
        super(message);
    }

    public HuaweiException(String message, Throwable cause) {
        super(message, cause);
    }
}
