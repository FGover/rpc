package com.fg.compress.service.Impl;

import com.fg.compress.service.Compressor;
import lombok.extern.slf4j.Slf4j;
import org.xerial.snappy.Snappy;

import java.io.IOException;

@Slf4j
public class SnappyCompressor implements Compressor {

    @Override
    public byte[] compress(byte[] bytes) {
        log.info("使用Snappy压缩方式");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("压缩数据不能为空");
        }
        try {
            return Snappy.compress(bytes);
        } catch (IOException e) {
            log.error("Snappy压缩异常", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] decompress(byte[] bytes) {
        log.info("使用Snappy解压方式");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("解压数据不能为空");
        }
        try {
            return Snappy.uncompress(bytes);
        } catch (IOException e) {
            log.error("Snappy解压异常", e);
            throw new RuntimeException(e);
        }
    }
}
