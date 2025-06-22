package com.fg.channel.handler;

import com.fg.compress.CompressFactory;
import com.fg.compress.service.Compressor;
import com.fg.enums.RequestType;
import com.fg.serialize.SerializerFactory;
import com.fg.serialize.service.Serializer;
import com.fg.transport.constant.MessageConstant;
import com.fg.transport.message.RpcRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;


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
 * timestamp：时间戳。8B
 * body：消息体，存放实际传输的数据。
 * * * <pre>
 *  *   0    1    2    3    4    5    6    7    8    9    10   11   12   13   14   15   16   17   18   19   20   21  22  23  24 25 26  27  28  29  30
 *  *   +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
 *  *   |    magic          |ver |head  len|    full length    | qt | ser|comp|              RequestId                |         timestamp           |
 *  *   +-----+-----+-------+----+----+----+----+-----------+----- ---+--------+----+----+----+----+----+----+---+---+----+----+----+----+----+----+
 *  *   |                                                                                                                                           |
 *  *   |                                         body                                                                                              |
 *  *   |                                                                                                                                           |
 *  *   +--------------------------------------------------------------------------------------------------------+---+----+----+----+----+----+----+
 *  *
 */
@Slf4j
public class RpcRequestEncoder extends MessageToByteEncoder<RpcRequest> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, RpcRequest rpcRequest, ByteBuf byteBuf)
            throws Exception {
        log.info("请求编码器执行: {}", rpcRequest);
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
        // 9.写入时间戳
        byteBuf.writeLong(rpcRequest.getTimestamp());
        // 如果是心跳请求，就不处理请求体
        if (rpcRequest.getRequestType() == RequestType.HEARTBEAT.getId()) {
            // 没有body，fullLength只包含header长度
            int fullLength = MessageConstant.HEADER_LENGTH;
            writeFullLength(byteBuf, fullLengthIndex, fullLength);
            return;
        }
        // 10.写入消息体
        log.info("请求编码器执行：序列化前数据长度：{}", rpcRequest.getRequestPayload().toString().length());
        // 获取序列化器
        Serializer serializer = SerializerFactory.getSerializer(rpcRequest.getSerializeType()).getSerializer();
        // 获取压缩器
        Compressor compressor = CompressFactory.getCompressor(rpcRequest.getCompressType()).getCompressor();
        byte[] body = serializer.serialize(rpcRequest.getRequestPayload());
        log.info("请求编码器执行：序列化后数据长度：{}", body.length);
        body = compressor.compress(body);
        log.info("请求编码器执行：压缩后数据长度：{}", body.length);
        byteBuf.writeBytes(body);
        // 重新处理报文的总长度
        int fullLength = MessageConstant.HEADER_LENGTH + body.length;
        writeFullLength(byteBuf, fullLengthIndex, fullLength);
    }

    /**
     * 写入消息报文总长度
     *
     * @param byteBuf
     * @param fullLengthIndex
     * @param fullLength
     */
    private void writeFullLength(ByteBuf byteBuf, int fullLengthIndex, int fullLength) {
        int currentWriterIndex = byteBuf.writerIndex();
        byteBuf.writerIndex(fullLengthIndex);
        byteBuf.writeInt(fullLength);
        byteBuf.writerIndex(currentWriterIndex);
    }
}
