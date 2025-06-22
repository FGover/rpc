package com.fg.enums;


public enum ResponseCode {

    SUCCESS((byte) 1, "SUCCESS"),
    FAILURE((byte) 2, "FAILURE");

    private final byte code;
    private final String message;

    ResponseCode(byte code, String message) {
        this.code = code;
        this.message = message;
    }

    public byte getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
