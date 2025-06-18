package com.fg.enums;

public enum RequestType {

    REQUEST((byte) 1, "普通请求"),
    HEARTBEAT((byte) 2, "心跳请求");

    private final byte id;
    private final String type;

    RequestType(byte id, String type) {
        this.id = id;
        this.type = type;
    }

    public byte getId() {
        return id;
    }

    public String getType() {
        return type;
    }
}
