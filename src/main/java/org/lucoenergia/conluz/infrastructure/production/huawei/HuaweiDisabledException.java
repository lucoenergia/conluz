package org.lucoenergia.conluz.infrastructure.production.huawei;

public class HuaweiDisabledException extends RuntimeException {

    public HuaweiDisabledException() {
        super("Huawei integration is disabled");
    }
}
