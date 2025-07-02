package com.fg.compress.service.impl;

import com.fg.compress.service.Compressor;
import com.github.luben.zstd.Zstd;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;

@Slf4j
public class ZstdCompressor implements Compressor {

    @Override
    public byte[] compress(byte[] bytes) {
        log.info("使用Zstd压缩方式");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("压缩数据不能为空");
        }
        try {
            byte[] compressed = Zstd.compress(bytes);
            // 先用4字节保存原始数据长度
            ByteBuffer buffer = ByteBuffer.allocate(4 + compressed.length);
            buffer.putInt(bytes.length);
            buffer.put(compressed);
            return buffer.array();
        } catch (Exception e) {
            log.error("Zstd压缩异常", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] decompress(byte[] bytes) {
        log.info("使用Zstd解压方式");
        if (bytes == null || bytes.length <= 4) {
            throw new IllegalArgumentException("解压数据不能为空或长度不合法");
        }
        try {
            // 先读取前4字节的原始长度
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            int originalLength = buffer.getInt();
            // 剩余为压缩数据
            byte[] compressed = new byte[bytes.length - 4];
            buffer.get(compressed);
            // 申请原始长度的数组解压
            byte[] restored = new byte[originalLength];
            long size = Zstd.decompress(restored, compressed);
            if (Zstd.isError(size)) {
                throw new RuntimeException("Zstd解压错误: " + Zstd.getErrorName(size));
            }
            return restored;
        } catch (Exception e) {
            log.error("Zstd解压异常", e);
            throw new RuntimeException(e);
        }
    }
}
