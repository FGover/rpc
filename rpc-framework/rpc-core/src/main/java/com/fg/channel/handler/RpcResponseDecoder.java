package com.fg.channel.handler;

import com.fg.enums.RequestType;
import com.fg.transport.message.MessageConstant;
import com.fg.transport.message.ResponsePayload;
import com.fg.transport.message.RpcRequest;
import com.fg.transport.message.RpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;

/**
 * <pre>
 *  *   0    1    2    3    4    5    6    7    8    9    10   11   12   13   14   15   16   17   18   19   20   21  22
 *  *   +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
 *  *   |       magic       |ver|header len|    full length    | rt | ser|comp|            requestId                   |
 *  *   +-------------------+----+----------+-----------------------+----+----+----+---------------------------------+
 *  *   |                                                                                                            |
 *  *   |                                                                                                            |
 *  *   |                                        body (serialized `data`)                                            |
 *  *   |                                                                                                            |
 *  *   +-----------------------------------------------------------------------------------------------------------+
 *  *
 *  * magic         : 4B   魔数，用于识别协议
 *  * version       : 1B   协议版本号
 *  * headerLength  : 2B   固定长度 = 23（多出1字节的 code）
 *  * fullLength    : 4B   报文总长度（头部+body）
 *  * rt           : 1B    响应类型
 *  * ser           : 1B   序列化类型（如JDK=0）
 *  * comp          : 1B   压缩类型（如无压缩=0）
 *  * requestId     : 8B   唯一标识，请求响应要对得上
 *  * body          : N    实际返回的数据 Object（使用 ser 类型序列化的结果）
 */
@Slf4j
public class RpcResponseDecoder extends LengthFieldBasedFrameDecoder {

    public RpcResponseDecoder() {
        super(
                MessageConstant.MAX_FRAME_LENGTH,  // maxFrameLength：单个数据包允许的最大长度（超过后直接丢弃，防止内存溢出）
                MessageConstant.LENGTH_FIELD_OFFSET,  // lengthFieldOffset：length 字段的偏移量（从字节流的哪个位置开始是 length 字段）
                MessageConstant.LENGTH_FIELD_LENGTH,  // lengthFieldLength：length 字段占用的字节数（fullLength 是 4 字节）
                MessageConstant.LENGTH_ADJUSTMENT,   // lengthAdjustment：从 length 字段的值开始，到真正需要读取的字节之间的差值（= 剩余 header 的长度 * -1）
                MessageConstant.INITIAL_BYTES_TO_STRIP   // initialBytesToStrip：跳过的字节数，设为 0 表示不跳过（我们自己来处理 magic、version 等头部字段）
        );
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        log.info("响应解码器执行: {}", in);
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        return decodeFrame(frame);
    }

    private Object decodeFrame(ByteBuf byteBuf) {
        // 读取魔数
        byte[] magic = new byte[4];
        byteBuf.readBytes(magic);
        if (!Arrays.equals(magic, MessageConstant.MAGIC)) {
            throw new IllegalArgumentException("非法魔数: " + Arrays.toString(magic));
        }
        // 读取协议版本号
        byte version = byteBuf.readByte();
        if (version > MessageConstant.VERSION) {
            throw new IllegalArgumentException("不支持的版本号: " + version);
        }
        // 读取 header 长度
        short headerLength = byteBuf.readShort();
        // 读取 fullLength
        int fullLength = byteBuf.readInt();
        // 读取序列化类型
        byte serializationType = byteBuf.readByte();
        // 读取压缩类型
        byte compressType = byteBuf.readByte();
        // 读取响应类型
        byte requestType = byteBuf.readByte();
        // 读取 requestId
        long requestId = byteBuf.readLong();
        // 封装响应对象
        RpcResponse rpcResponse = new RpcResponse();
        rpcResponse.setRequestId(requestId);
        rpcResponse.setRequestType(requestType);
        rpcResponse.setCompressType(compressType);
        rpcResponse.setSerializeType(serializationType);
        // 如果是心跳响应，无body
        if (requestType == RequestType.HEARTBEAT.getId()) {
            return rpcResponse;
        }
        // 读取 body
        byte[] body = new byte[fullLength - headerLength];
        byteBuf.readBytes(body);
        ResponsePayload responsePayload;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(body))) {
            responsePayload = (ResponsePayload) ois.readObject();
            // 设置响应对象
            rpcResponse.setResponsePayload(responsePayload);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return rpcResponse;
    }
}
