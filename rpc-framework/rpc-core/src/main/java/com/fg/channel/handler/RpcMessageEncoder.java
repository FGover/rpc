package com.fg.channel.handler;

import com.fg.transport.message.MessageConstant;
import com.fg.transport.message.RequestPayload;
import com.fg.transport.message.RpcRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;


/**
 * 出站时，第一个经过的处理器
 * 把 Java 对象 RpcRequest 编码成字节序列（ByteBuf），以便通过网络传输。
 * ------
 * 自定义协议编码器
 * magic：魔数，用于标识协议类型，便于接收方识别。4B
 * version：协议版本号。1B
 * headerLength：头部长度。2B
 * fullLength：消息报文总长度。4B
 * serializeType：序列化类型。1B
 * compressType：压缩类型。1B
 * requestType：请求类型。1B
 * requestId：请求ID。8B
 * body：消息体，存放实际传输的数据。
 * * * <pre>
 *  *   0    1    2    3    4    5    6    7    8    9    10   11   12   13   14   15   16   17   18   19   20   21  22
 *  *   +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
 *  *   |    magic          |ver |head  len|    full length    | qt | ser|comp|              RequestId                |
 *  *   +-----+-----+-------+----+----+----+----+-----------+----- ---+--------+----+----+----+----+----+----+---+---+
 *  *   |                                                                                                             |
 *  *   |                                         body                                                                |
 *  *   |                                                                                                             |
 *  *   +--------------------------------------------------------------------------------------------------------+---+
 *  *
 */
@Slf4j
public class RpcMessageEncoder extends MessageToByteEncoder<RpcRequest> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, RpcRequest rpcRequest, ByteBuf byteBuf)
            throws Exception {
        // 1.写入魔数
        byteBuf.writeBytes(MessageConstant.MAGIC);
        // 2.写入版本号
        byteBuf.writeByte(MessageConstant.VERSION);
        // 3.写入头部长度
        byteBuf.writeShort(MessageConstant.HEADER_LENGTH);
        // 4.写入消息报文总长度
        int fullLengthIndex = byteBuf.writerIndex(); // 记录当前写指针
        byteBuf.writeInt(0); // 先占位
        // 5.写入序列化类型
        byteBuf.writeByte(rpcRequest.getSerializeType());
        // 6.写入压缩类型
        byteBuf.writeByte(rpcRequest.getCompressType());
        // 7.写入请求类型
        byteBuf.writeByte(rpcRequest.getRequestType());
        // 8.写入请求ID
        byteBuf.writeLong(rpcRequest.getRequestId());
        // 9.写入消息体
        byte[] body = getBodyBytes(rpcRequest.getRequestPayload());
        byteBuf.writeBytes(body);
        // 重新处理报文的总长度
        int fullLength = MessageConstant.HEADER_LENGTH + body.length;
        // 先保存当前写指针的位置
        int currentWriterIndex = byteBuf.writerIndex();
        // 将写指针移动到消息报文总长度的位置
        byteBuf.writerIndex(fullLengthIndex); // 回到full length占位的位置
        byteBuf.writeInt(fullLength); // 写入真正的 full length
        byteBuf.writerIndex(currentWriterIndex); // 还原写指针
    }

    /**
     * 将序列化请求体转为字节数组
     * 将 Java 对象转换成可以通过网络传输的格式（byte 流），通常用于 RPC 通信中的请求体部分（body 部分）。
     *
     * @param requestPayload
     * @return
     */
    private byte[] getBodyBytes(RequestPayload requestPayload) {
        try {
            // 创建一个内存缓冲区，接收序列化后的字节数据
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            // 创建一个对象输出流，用于将 Java 对象序列化到内存缓冲区
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(requestPayload);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            log.error("序列化失败", e);
            throw new RuntimeException(e);
        }
    }
}
