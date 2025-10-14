package com.fg.config;

import com.fg.IdGenerator;
import com.fg.compress.CompressorFactory;
import com.fg.compress.service.Compressor;
import com.fg.discovery.RegistryConfig;
import com.fg.loadbalancer.service.impl.RoundRobinLoadBalancer;
import com.fg.loadbalancer.service.LoadBalancer;
import com.fg.serialize.SerializerFactory;
import com.fg.serialize.service.Serializer;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class XmlResolver {

    public void loadMergedConfig(Configuration configuration) {
        // 先加载 core 默认配置
        loadFromXml(configuration, "rpc-config.xml");
        // 再加载 provider 自定义配置（覆盖核心配置）
        loadFromXml(configuration, "provider-rpc-config.xml");
        // 再加载 consumer 自定义配置
        loadFromXml(configuration, "consumer-rpc-config.xml");
    }

    /**
     * 从xml文件中加载配置
     *
     * @param configuration
     */
    public void loadFromXml(Configuration configuration, String fileName) {
        try {
            // 加载resources目录下的配置文件
            InputStream inputStream = Configuration.class.getClassLoader().getResourceAsStream(fileName);
            if (inputStream == null) {
                log.error("未找到rpc-config.xml配置文件，使用默认配置");
                return;
            }
            // 解析XML文档
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 禁用外部实体
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setValidating(false);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            // 设置 EntityResolver（关键！）
            builder.setEntityResolver((publicId, systemId) -> {
                if (systemId != null && systemId.endsWith("rpc-config.dtd")) {
                    InputStream dtdStream = Configuration.class.getClassLoader().getResourceAsStream("rpc-config.dtd");
                    if (dtdStream == null) {
                        throw new RuntimeException("rpc-config.dtd not found in classpath");
                    }
                    return new org.xml.sax.InputSource(dtdStream);
                }
                return null;
            });
            Document doc = builder.parse(inputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            // 解析所有配置
            Integer port = resolvePort(doc, xPath);
            if (port != null) {
                configuration.setPort(port);
            }
            String applicationName = resolveApplicationName(doc, xPath);
            if (applicationName != null) {
                configuration.setApplicationName(applicationName);
            }
            configuration.setRegistryConfig(resolveRegistryConfig(doc, xPath));
            String serializeType = resolveSerializeType(doc, xPath);
            if (serializeType != null) {
                configuration.setSerializeType(serializeType);
            }
            String compressType = resolveCompressType(doc, xPath);
            if (compressType != null) {
                configuration.setCompressType(compressType);
            }
            ObjectWrapper<Serializer> serializerObjectWrapper = resolveSerializer(doc, xPath);
            if (serializerObjectWrapper != null) {
                SerializerFactory.addSerializer(serializerObjectWrapper);
            }
            ObjectWrapper<Compressor> compressorObjectWrapper = resolveCompressor(doc, xPath);
            if (compressorObjectWrapper != null) {
                CompressorFactory.addCompressor(compressorObjectWrapper);
            }
            configuration.setLoadBalancer(resolveLoadBalancer(doc, xPath));
            configuration.setIdGenerator(resolveIdGenerator(doc, xPath));
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new RuntimeException("XML 配置加载异常", e);
        }
    }

    /**
     * 提取XML节点中的属性值
     *
     * @param doc        XML 文档对象
     * @param xPath      XPath 工具
     * @param expression XPath 表达式
     * @return 节点的文本内容，找不到时返回 null
     */
    private String parseString(Document doc, XPath xPath, String expression) {
        try {
            XPathExpression express = xPath.compile(expression);
            Node targetNode = (Node) express.evaluate(doc, XPathConstants.NODE);
            return targetNode != null ? targetNode.getTextContent().trim() : null;
        } catch (XPathExpressionException e) {
            log.error("解析 XPath 表达式失败: {}", expression, e);
        }
        return null;
    }

    /**
     * 解析某个节点的所有属性，返回属性名和属性值的映射
     *
     * @param doc        XML 文档
     * @param xPath      XPath 对象
     * @param expression XPath 表达式，定位到某个节点
     * @return 属性名 -> 属性值 映射
     */
    private Map<String, String> parseAttribute(Document doc, XPath xPath, String expression) {
        Map<String, String> attrMap = new HashMap<>();
        try {
            Node node = (Node) xPath.compile(expression).evaluate(doc, XPathConstants.NODE);
            if (node != null && node.hasAttributes()) {
                NamedNodeMap attributes = node.getAttributes();
                for (int i = 0; i < attributes.getLength(); i++) {
                    Node attr = attributes.item(i);
                    attrMap.put(attr.getNodeName(), attr.getNodeValue());
                }
            }
        } catch (XPathExpressionException e) {
            log.error("解析属性失败: {}", expression, e);
        }
        return attrMap;
    }

    /**
     * 根据XML节点中class属性反射实例化对象
     *
     * @param doc              XML 文档对象
     * @param xPath            XPath 工具
     * @param expression       XPath 表达式
     * @param paramType        构造参数类型
     * @param param            构造参数值（无参传空）
     * @param <T>返回的对象类型（需要强转）
     * @return 反射生成的对象实例
     */
    @SuppressWarnings("unchecked")
    private <T> T parseObject(Document doc, XPath xPath, String expression, Class<?>[] paramType, Object... param) {
        try {
            XPathExpression express = xPath.compile(expression);
            Node targetNode = (Node) express.evaluate(doc, XPathConstants.NODE);
            if (targetNode == null) return null;
            Node classNode = targetNode.getAttributes().getNamedItem("class");
            if (classNode == null) return null;
            String className = classNode.getNodeValue();
            if (className == null || className.isEmpty()) return null;
            Class<?> clazz = Class.forName(className);
            Object instance;
            if (paramType == null) {
                instance = clazz.getConstructor().newInstance();
            } else {
                instance = clazz.getConstructor(paramType).newInstance(param);
            }
            return (T) instance;
        } catch (Exception e) {
            log.warn("反射创建对象失败: {}，忽略该配置", expression, e);
            return null;
        }
    }

    /**
     * 解析XML文档中的端口配置
     *
     * @param doc
     * @param xPath
     * @return
     */
    private Integer resolvePort(Document doc, XPath xPath) {
        String expression = "/configuration/port";
        String portString = parseString(doc, xPath, expression);
        if (portString == null || portString.isEmpty()) {
            log.error("未找到端口配置，使用默认端口: 8088");
            return null;
        }
        try {
            return Integer.parseInt(portString.trim());
        } catch (NumberFormatException e) {
            log.error("端口配置格式错误: {}", portString, e);
            return null;
        }
    }

    /**
     * 解析XML文档中的应用名称配置
     *
     * @param doc
     * @param xPath
     * @return
     */
    private String resolveApplicationName(Document doc, XPath xPath) {
        String expression = "/configuration/applicationName";
        String appName = parseString(doc, xPath, expression);
        if (appName == null || appName.isEmpty()) {
            return null;
        }
        return appName.trim();
    }

    /**
     * 解析XML文档中的请求id生成器配置
     *
     * @param doc
     * @param xPath
     * @return
     */
    private IdGenerator resolveIdGenerator(Document doc, XPath xPath) {
        String expression = "/configuration/idGenerator";
        Map<String, String> attrMap = parseAttribute(doc, xPath, expression);
        // 设置默认值
        long dataCenterId = 1L;
        long machineId = 2L;
        try {
            if (attrMap.containsKey("dataCenterId")) {
                dataCenterId = Long.parseLong(attrMap.get("dataCenterId"));
            }
            if (attrMap.containsKey("machineId")) {
                machineId = Long.parseLong(attrMap.get("machineId"));
            }
        } catch (NumberFormatException e) {
            log.warn("idGenerator 配置格式错误，使用默认值 dataCenterId=1, MachineId=2");
        }
        return new IdGenerator(dataCenterId, machineId);
    }

    /**
     * 解析XML文档中的负载均衡配置
     *
     * @param doc
     * @param xPath
     * @return
     */
    private LoadBalancer resolveLoadBalancer(Document doc, XPath xPath) {
        String expression = "/configuration/loadBalancer";
        try {
            LoadBalancer lb = parseObject(doc, xPath, expression, null);
            return lb != null ? lb : new RoundRobinLoadBalancer();
        } catch (RuntimeException e) {
            log.error("解析负载均衡器失败，使用默认RoundRobinLoadBalancer", e);
            return new RoundRobinLoadBalancer();
        }
    }

    /**
     * 解析XML文档中的压缩类型配置
     *
     * @param doc
     * @param xPath
     * @return
     */
    private String resolveCompressType(Document doc, XPath xPath) {
        String expression = "/configuration/compressType";
        Map<String, String> attrMap = parseAttribute(doc, xPath, expression);
        return attrMap.getOrDefault("type", "gzip");
    }


    private ObjectWrapper<Compressor> resolveCompressor(Document doc, XPath xPath) {
        String expression = "/configuration/compressor";
        try {
            Compressor compressor = parseObject(doc, xPath, expression, null);
            Map<String, String> attrMap = parseAttribute(doc, xPath, expression);
            if (compressor == null || attrMap.isEmpty()) return null;
            return new ObjectWrapper<>(Byte.parseByte(attrMap.getOrDefault("code", "0")), attrMap.getOrDefault("type", "gzip"), compressor);
        } catch (Exception e) {
            log.warn("加载 Compressor 失败，忽略该配置", e);
            return null;
        }
    }


    /**
     * 解析XML文档中的序列化类型配置
     *
     * @param doc
     * @param xPath
     * @return
     */
    private String resolveSerializeType(Document doc, XPath xPath) {
        String expression = "/configuration/serializeType";
        Map<String, String> attrMap = parseAttribute(doc, xPath, expression);
        return attrMap.getOrDefault("type", "jdk");
    }

    private ObjectWrapper<Serializer> resolveSerializer(Document doc, XPath xPath) {
        String expression = "/configuration/serializer";
        try {
            Serializer serializer = parseObject(doc, xPath, expression, null);
            Map<String, String> attrMap = parseAttribute(doc, xPath, expression);
            if (serializer == null || attrMap.isEmpty()) return null;
            return new ObjectWrapper<>(Byte.parseByte(attrMap.getOrDefault("code", "0")), attrMap.getOrDefault("type", "jdk"), serializer);
        } catch (Exception e) {
            log.warn("加载 Serializer 失败，忽略该配置", e);
            return null;
        }
    }

    /**
     * 解析XML文档中的注册中心配置
     *
     * @param doc
     * @param xPath
     * @return
     */
    private RegistryConfig resolveRegistryConfig(Document doc, XPath xPath) {
        String expression = "/configuration/registry";
        Map<String, String> attrMap = parseAttribute(doc, xPath, expression);
        if (!attrMap.containsKey("url") || attrMap.get("url").isEmpty()) {
            log.warn("未配置 registry 的 url 属性，将使用默认地址");
            return new RegistryConfig("zookeeper://127.0.0.1:2181");
        }
        return new RegistryConfig(attrMap.get("url"));
    }
}
