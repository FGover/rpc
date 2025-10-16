package com.fg.spi;

import com.fg.config.ObjectWrapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SpiHandler {
    // SPI 配置文件所在的基础路径
    private static final String BASE_PATH = "META-INF/rpc-services/";
    // SPI 内容缓存
    private static final Map<String, List<String>> SPI_CONTENT = new ConcurrentHashMap<>();
    // 缓存接口对应的实例包装类
    private static final Map<Class<?>, List<ObjectWrapper<?>>> SPI_IMPLEMENTATIONS = new ConcurrentHashMap<>();

    /**
     * 从SPI配置中加载并返回某个接口的实现类实例
     *
     * @param clazz
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public synchronized static <T> ObjectWrapper<T> get(Class<T> clazz) {
        // 1. 先从缓存取
        List<ObjectWrapper<?>> cached = SPI_IMPLEMENTATIONS.get(clazz);
        if (cached != null && !cached.isEmpty()) {
            ObjectWrapper<T> wrapper = (ObjectWrapper<T>) cached.get(0);
            log.info("[SPI] 从缓存中获取实现：interface={}, implClass={}, code={}, type={}",
                    clazz.getName(),
                    wrapper.getImpl().getClass().getName(),
                    wrapper.getCode(),
                    wrapper.getType());
            return wrapper;
        }
        // 2. 加载 SPI 配置
        loadSpiFile(clazz);
        List<String> implConfigs = SPI_CONTENT.get(clazz.getName());
        if (implConfigs == null || implConfigs.isEmpty()) {
            throw new RuntimeException("未找到接口 " + clazz.getName() + " 的 SPI 实现类！");
        }
        // 3. 解析第一个配置
        ParsedSpiConfig config = parseSpiConfig(implConfigs.get(0));
        try {
            ObjectWrapper<T> wrapper = createWrapper(clazz, config);
            cacheWrapper(clazz, wrapper);
            log.info("[SPI] 成功加载 SPI 实现：interface={}, implClass={}, code={}, type={}",
                    clazz.getName(), config.className, config.code, config.type);
            return wrapper;
        } catch (Exception e) {
            log.error("[SPI] 实例化 SPI 类失败：{}", config.className, e);
            throw new RuntimeException("实例化 SPI 类失败：" + config.className, e);
        }
    }

    /**
     * 返回某个接口的全部实现实例列表
     *
     * @param clazz
     * @param <T>
     * @return
     */
    public static <T> List<ObjectWrapper<T>> getAll(Class<T> clazz) {
        loadSpiFile(clazz);
        List<String> implConfigs = SPI_CONTENT.get(clazz.getName());
        if (implConfigs == null || implConfigs.isEmpty()) {
            throw new RuntimeException("未找到接口 " + clazz.getName() + " 的 SPI 实现类！");
        }
        List<ObjectWrapper<T>> result = new ArrayList<>();
        for (String configLine : implConfigs) {
            ParsedSpiConfig config = parseSpiConfig(configLine);
            try {
                ObjectWrapper<T> wrapper = createWrapper(clazz, config);
                result.add(wrapper);
                cacheWrapper(clazz, wrapper);
                log.info("[SPI] 加载 SPI 实现成功：interface={}, implClass={}, code={}, type={}",
                        clazz.getName(), config.className, config.code, config.type);
            } catch (Exception e) {
                log.error("[SPI] 实例化 SPI 实现失败：interface={}, className={}", clazz.getName(), config.className, e);
            }
        }
        return result;
    }

    /**
     * 加载SPI文件（仅当缓存中没有时才加载）
     *
     * @param clazz
     */
    private static void loadSpiFile(Class<?> clazz) {
        String interfaceName = clazz.getName();
        if (SPI_CONTENT.containsKey(interfaceName)) {
            return;
        }
        List<String> implClassList = new ArrayList<>();
        try {
            // 获取类加载器
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            // 查找对应的资源文件
            Enumeration<URL> resources = classLoader.getResources(BASE_PATH + interfaceName);
            // 遍历所有SPI文件
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 去除空行和注释（以#开头）
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            implClassList.add(line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("加载 SPI 文件失败，接口：{}", interfaceName, e);
        }
        // 合并多个 SPI 文件的配置，避免覆盖
        SPI_CONTENT.merge(interfaceName, implClassList, (oldList, newList) -> {
            oldList.addAll(newList);
            return oldList;
        });
    }

    /**
     * 解析SPI配置行，格式：code-type-className
     *
     * @param configLine
     * @return
     */
    private static ParsedSpiConfig parseSpiConfig(String configLine) {
        String[] parts = configLine.split("-", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("SPI 配置格式错误（应为 code-type-className）：[" + configLine + "]");
        }
        byte code;
        try {
            code = Byte.parseByte(parts[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SPI code 解析失败：" + configLine, e);
        }
        String type = parts[1];
        String className = parts[2];
        return new ParsedSpiConfig(code, type, className);
    }

    /**
     * 根据配置创建ObjectMapper实例
     *
     * @param iface
     * @param config
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    private static <T> ObjectWrapper<T> createWrapper(Class<T> iface, ParsedSpiConfig config) {
        try {
            Class<?> implClass = Class.forName(config.className);
            if (!iface.isAssignableFrom(implClass)) {
                throw new RuntimeException("类 " + config.className + " 未实现接口 " + iface.getName());
            }
            Constructor<?> constructor = implClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            T instance = (T) constructor.newInstance();
            ObjectWrapper<T> wrapper = new ObjectWrapper<>();
            wrapper.setCode(config.code);
            wrapper.setType(config.type);
            wrapper.setImpl(instance);
            return wrapper;
        } catch (ClassNotFoundException |
                 NoSuchMethodException |
                 InvocationTargetException |
                 InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException("创建 SPI 实例失败，类名：" + config.className, e);
        }
    }


    /**
     * 缓存ObjectMapper，避免重复添加
     *
     * @param iface
     * @param wrapper
     */
    private static void cacheWrapper(Class<?> iface, ObjectWrapper<?> wrapper) {
        SPI_IMPLEMENTATIONS.compute(iface, (k, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            // 避免重复缓存同一实现类
            boolean exists = list.stream().anyMatch(w ->
                    w.getImpl().getClass().getName().equals(wrapper.getImpl().getClass().getName())
            );
            if (!exists) {
                list.add(wrapper);
            }
            return list;
        });
    }

    private static class ParsedSpiConfig {
        final byte code;
        final String type;
        final String className;

        ParsedSpiConfig(byte code, String type, String className) {
            this.code = code;
            this.type = type;
            this.className = className;
        }
    }

}
