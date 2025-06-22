package com.fg.transport.message;

public class MessageConstant {

    // 魔数，用于标识协议类型，便于接收方识别。4B
    public static final byte[] MAGIC = new byte[]{'r', 'p', 'c', 0x01};
    // 协议版本号。1B
    public static final byte VERSION = 1;
    // 头部长度。2B
    public static final int HEADER_LENGTH = 30;

    // netty 解码器参数：
    public static final int MAX_FRAME_LENGTH = 1024 * 1024;   // 最大帧长度限制
    public static final int LENGTH_FIELD_OFFSET = 7;    // fullLength 从第 7 字节开始
    public static final int LENGTH_FIELD_LENGTH = 4;   // fullLength 占 4 字节
    public static final int LENGTH_ADJUSTMENT = -11;   // fullLength 中包含了剩余 header的 11 字节，所以减去
    public static final int INITIAL_BYTES_TO_STRIP = 0;   // 不跳过任何字节

}
