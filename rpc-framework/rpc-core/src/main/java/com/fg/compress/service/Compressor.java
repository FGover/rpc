package com.fg.compress.service;

public interface Compressor {

    // 压缩
    byte[] compress(byte[] bytes);

    // 解压
    byte[] decompress(byte[] bytes);
}
