package com.fg.utils.nacos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NacosNode {

    private String serviceName;  // 服务名
    private String group;  // 分组名
    private String ip; // ip
    private int port; // 端口
    private boolean healthy; // 是否健康
    private double weight; // 权重
}
