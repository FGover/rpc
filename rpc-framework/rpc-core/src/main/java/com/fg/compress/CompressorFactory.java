package com.fg.compress;

import com.fg.compress.service.Compressor;
import com.fg.compress.service.impl.GzipCompressor;
import com.fg.compress.service.impl.SnappyCompressor;
import com.fg.compress.service.impl.ZstdCompressor;
import com.fg.config.ObjectWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CompressorFactory {

    // 缓存：名称 -> 包装器
    private static final Map<String, ObjectWrapper<Compressor>> COMPRESSOR_CACHE = new ConcurrentHashMap<>();

    // 缓存：编号 -> 包装器
    private static final Map<Byte, ObjectWrapper<Compressor>> COMPRESSOR_CACHE_CODE = new ConcurrentHashMap<>();

    // 静态初始化支持的压缩器
    static {
        ObjectWrapper<Compressor> gzipWrapper = new ObjectWrapper<>((byte) 1, "gzip", new GzipCompressor());
        ObjectWrapper<Compressor> snappyWrapper = new ObjectWrapper<>((byte) 2, "snappy", new SnappyCompressor());
        ObjectWrapper<Compressor> zstdWrapper = new ObjectWrapper<>((byte) 3, "zstd", new ZstdCompressor());

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
    public static ObjectWrapper<Compressor> getCompressor(String compressType) {
        ObjectWrapper<Compressor> wrapper = COMPRESSOR_CACHE.get(compressType);
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
    public static ObjectWrapper<Compressor> getCompressor(Byte compressCode) {
        ObjectWrapper<Compressor> wrapper = COMPRESSOR_CACHE_CODE.get(compressCode);
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
    public static ObjectWrapper<Compressor> getDefaultCompressor() {
        return COMPRESSOR_CACHE.get("gzip");
    }

    /**
     * 添加压缩器
     *
     * @param wrapper
     */
    public static void addCompressor(ObjectWrapper<Compressor> wrapper) {
        COMPRESSOR_CACHE.put(wrapper.getType(), wrapper);
        COMPRESSOR_CACHE_CODE.put(wrapper.getCode(), wrapper);
    }
}
