package com.fg.compress.service.impl;

import com.fg.compress.service.Compressor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class GzipCompressor implements Compressor {

    /**
     * 将数据压缩成字节数组
     *
     * @param bytes
     * @return
     */
    @Override
    public byte[] compress(byte[] bytes) {
        log.info("使用Gzip压缩方式");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("压缩数据不能为空");
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(bos)) {
            gzipOut.write(bytes);
            gzipOut.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("Gzip压缩异常", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 将字节数组解压成数据
     *
     * @param bytes
     * @return
     */
    @Override
    public byte[] decompress(byte[] bytes) {
        log.info("使用Gzip解压方式");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("解压数据不能为空");
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             GZIPInputStream gzipIn = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("Gzip解压异常", e);
            throw new RuntimeException(e);
        }
    }

}
