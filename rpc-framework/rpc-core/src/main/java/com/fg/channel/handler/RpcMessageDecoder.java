package com.fg.channel.handler;

import com.fg.transport.message.MessageConstant;
import com.fg.transport.message.RequestPayload;
import com.fg.transport.message.RpcRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Arrays;

@Slf4j
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {
    public RpcMessageDecoder() {
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
        Object decode = super.decode(ctx, in);
        if (decode instanceof ByteBuf byteBuf) {
            return decodeFrame(byteBuf);
        }
        return null;
    }

    private Object decodeFrame(ByteBuf byteBuf) {
        // 1.读取魔数并校验
        byte[] magic = new byte[4];
        byteBuf.readBytes(magic);
        if (!Arrays.equals(magic, MessageConstant.MAGIC)) {
            throw new IllegalArgumentException("非法的魔数：" + Arrays.toString(magic));
        }
        // 2.读取版本号并校验
        byte version = byteBuf.readByte();
        if (version > MessageConstant.VERSION) {
            throw new IllegalArgumentException("非法的版本号：" + version);
        }
        // 3.读取头部长度
        short headerLength = byteBuf.readShort();
        // 4.读取总长度
        int fullLength = byteBuf.readInt();
        // 5.读取序列化类型
        byte serializeType = byteBuf.readByte();
        // 6.读取压缩类型
        byte compressType = byteBuf.readByte();
        // 7.读取请求类型
        byte requestType = byteBuf.readByte();
        // 8.读取请求ID
        long requestId = byteBuf.readLong();
        // 9.读取消息体
        byte[] body = new byte[fullLength - headerLength];
        byteBuf.readBytes(body);
        // 10.反序列化 body 成 Java 对象
        RequestPayload requestPayload;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(body))) {
            requestPayload = (RequestPayload) ois.readObject();
        } catch (Exception e) {
            log.error("反序列化 body 失败", e);
            throw new RuntimeException(e);
        }
        // 11.封装成 RpcRequest 对象
        RpcRequest rpcRequest = new RpcRequest();
        rpcRequest.setRequestId(requestId);
        rpcRequest.setRequestType(requestType);
        rpcRequest.setSerializeType(serializeType);
        rpcRequest.setCompressType(compressType);
        rpcRequest.setRequestPayload(requestPayload);
        return rpcRequest;
    }
}
