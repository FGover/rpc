package com.fg.compress;

import com.fg.compress.service.Impl.GzipCompressor;
import com.fg.compress.service.Impl.SnappyCompressor;
import com.fg.compress.service.Impl.ZstdCompressor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CompressFactory {

    // 缓存：名称 -> 包装器
    private static final ConcurrentHashMap<String, CompressWrapper> COMPRESSOR_CACHE = new ConcurrentHashMap<>();

    // 缓存：编号 -> 包装器
    private static final ConcurrentHashMap<Byte, CompressWrapper> COMPRESSOR_CACHE_CODE = new ConcurrentHashMap<>();

    // 静态初始化支持的压缩器
    static {
        CompressWrapper gzipWrapper = new CompressWrapper((byte) 1, "gzip", new GzipCompressor());
        CompressWrapper snappyWrapper = new CompressWrapper((byte) 2, "snappy", new SnappyCompressor());
        CompressWrapper zstdWrapper = new CompressWrapper((byte) 3, "zstd", new ZstdCompressor());

        COMPRESSOR_CACHE.put("gzip", gzipWrapper);
        COMPRESSOR_CACHE.put("snappy", snappyWrapper);
        COMPRESSOR_CACHE.put("zstd", zstdWrapper);

        COMPRESSOR_CACHE_CODE.put((byte) 1, gzipWrapper);
        COMPRESSOR_CACHE_CODE.put((byte) 2, snappyWrapper);
        COMPRESSOR_CACHE_CODE.put((byte) 3, zstdWrapper);
    }

    /**
     * 根据名称获取压缩器
     *
     * @param compressType
     * @return
     */
    public static CompressWrapper getCompressor(String compressType) {
        CompressWrapper wrapper = COMPRESSOR_CACHE.get(compressType);
        if (wrapper == null) {
            log.error("未找到您配置的{}压缩工具，使用默认gzip压缩方式", compressType);
            return getDefaultCompressor();
        }
        return wrapper;
    }

    /**
     * 根据编号获取压缩器
     *
     * @param compressCode
     * @return
     */
    public static CompressWrapper getCompressor(Byte compressCode) {
        CompressWrapper wrapper = COMPRESSOR_CACHE_CODE.get(compressCode);
        if (wrapper == null) {
            log.error("未找到您配置的{}压缩工具，使用默认gzip压缩方式", compressCode);
            return getDefaultCompressor();
        }
        return wrapper;
    }

    /**
     * 获取默认压缩器
     *
     * @return
     */
    public static CompressWrapper getDefaultCompressor() {
        return COMPRESSOR_CACHE.get("gzip");
    }

    /**
     * 添加压缩器
     *
     * @param wrapper
     */
    public static void addCompressor(CompressWrapper wrapper) {
        COMPRESSOR_CACHE.put(wrapper.getName(), wrapper);
        COMPRESSOR_CACHE_CODE.put(wrapper.getCode(), wrapper);
    }
}
