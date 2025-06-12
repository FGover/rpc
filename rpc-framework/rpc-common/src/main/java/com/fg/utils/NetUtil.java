package com.fg.utils;

import com.fg.exception.NetworkException;
import lombok.extern.slf4j.Slf4j;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;


/**
 * 获取当前机器的局域网IPV4地址
 */
@Slf4j
public class NetUtil {
    public static String getIP() {
        try {
            // 获取所有网卡
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                // 跳过无效接口（未启用的、回环的、虚拟的）
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                // 获取网卡上的所有IP地址
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // 跳过IPv6地址和回环地址
                    if (addr instanceof Inet6Address || addr.isLoopbackAddress()) {
                        continue;
                    }
                    String ipAddress = addr.getHostAddress();
                    if (log.isDebugEnabled()) {
                        log.debug("局域网IP地址: {}", ipAddress);
                    }
                    return ipAddress;
                }
            }
            throw new NetworkException();
        } catch (SocketException e) {
            throw new NetworkException(e);
        }
    }

    public static void main(String[] args) {
        String ip = NetUtil.getIP();
        System.out.println(ip);
    }
}
