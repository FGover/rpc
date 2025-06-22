package com.fg.transport.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RpcResponse {

    // 请求id
    private Long requestId;
    // 请求类型
    private byte requestType;
    // 压缩类型
    private byte compressType;
    // 序列化类型
    private byte serializeType;
    // 时间戳
    private Long timestamp;
    // 响应体
    private ResponsePayload responsePayload;
}
