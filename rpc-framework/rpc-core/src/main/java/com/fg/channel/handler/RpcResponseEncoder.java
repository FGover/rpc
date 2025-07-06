package com.fg.channel.handler;

import com.fg.compress.CompressorFactory;
import com.fg.compress.service.Compressor;
import com.fg.enums.RequestType;
import com.fg.serialize.SerializerFactory;
import com.fg.serialize.service.Serializer;
import com.fg.transport.constant.MessageConstant;
import com.fg.transport.message.RpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/*
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
 *  *   |    magic          |ver |head  len|    full length    | rt | ser|comp|              RequestId                |
 *  *   +-----+-----+-------+----+----+----+----+-----------+----- ---+--------+----+----+----+----+----+----+---+----+
 *  *   |                                                                                                             |
 *  *   |                                         body                                                                |
 *  *   |                                                                                                             |
 *  *   +--------------------------------------------------------------------------------------------------------+----+
 *  *
 */
@Slf4j
public class RpcResponseEncoder extends MessageToByteEncoder<RpcResponse> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, RpcResponse rpcResponse, ByteBuf out)
            throws Exception {
        log.info("响应编码器执行: {}", rpcResponse);
        // 写入魔数
        out.writeBytes(MessageConstant.MAGIC);
        // 写入版本号
        out.writeByte(MessageConstant.VERSION);
        // 写入头部长度
        out.writeShort(MessageConstant.HEADER_LENGTH);
        // 写入消息总长度
        int fullLengthIndex = out.writerIndex();
        out.writeInt(0);
        // 写入序列化类型
        out.writeByte(rpcResponse.getSerializeType());
        // 写入压缩类型
        out.writeByte(rpcResponse.getCompressType());
        // 写入请求类型
        out.writeByte(rpcResponse.getRequestType());
        // 写入请求ID
        out.writeLong(rpcResponse.getRequestId());
        // 写入时间戳
        out.writeLong(System.currentTimeMillis());
        // 如果是心跳请求，就不处理请求体
//        if (rpcResponse.getRequestType() == RequestType.HEARTBEAT.getId()) {
//            int fullLength = MessageConstant.HEADER_LENGTH;
//            writeFullLength(out, fullLengthIndex, fullLength);
//            return;
//        }
        // 写入消息体
        log.info("响应编码器执行: 序列化前数据长度：{}", rpcResponse.getResponsePayload().toString().length());
        // 获取序列化器
        Serializer serializer = SerializerFactory.getSerializer(rpcResponse.getSerializeType()).getImpl();
        // 获取压缩器
        Compressor compressor = CompressorFactory.getCompressor(rpcResponse.getCompressType()).getImpl();
        byte[] body = serializer.serialize(rpcResponse.getResponsePayload());
        log.info("响应编码器执行: 序列化后数据长度：{}", body.length);
        body = compressor.compress(body);
        log.info("响应编码器执行: 压缩后数据长度：{}", body.length);
        out.writeBytes(body);
        // 回填总长度
        int fullLength = MessageConstant.HEADER_LENGTH + body.length;
        writeFullLength(out, fullLengthIndex, fullLength);
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
