手写rpc框架


![image](https://github.com/user-attachments/assets/ace76d12-667a-41a9-be3d-40eb2162206e)

## 完整调用流程
1.Provider 启动  
- RpcBootstrap.start() 启动 Netty 服务端
- 绑定端口成功后，向注册中心注册服务
- 服务端 pipeline：LoggingHandler → RpcRequestDecoder → RpcResponseEncoder → RpcRequestHandler  

2.Consumer 启动  
- 从注册中心发现服务实例
- 建立 Netty 客户端连接
- 客户端 pipeline：RpcRequestEncoder → RpcResponseDecoder → RpcResponseHandler 

3.Consumer 发送请求  
- 调用远程服务时，通过 RpcRequestEncoder 编码请求
- 发送到 Provider 的 Netty 服务端

4.Provider 接收并解码  
- 服务端通过 RpcRequestDecoder 解码请求
- 解码后的 RpcRequest 传递给 RpcRequestHandler 处理业务
- 业务处理完成后，通过 RpcResponseEncoder 编码响应
- 发送响应给 Consumer

5.Consumer 接收响应  
- 通过 RpcResponseDecoder 解码响应
- 解码后的 RpcResponse 传递给 RpcResponseHandler 处理



### 负载均衡是指将请求合理分发到多个服务器节点上，从而提高系统的并发能力、容错性和可用性。  
解决的问题是：当有多个服务提供者时，我们如何选择其中一个去调用？  
负载均衡器通常位于客户端和服务器之间，根据一定的算法将请求分发到不同的服务器节点上。常见的负载均衡算法有轮询、随机、最少连接、哈希等。 
在RPC框架中，负载均衡器通常发生在服务消费方（Client）拿到服务注册中心返回的多个服务实例之后，在真正发起请求之前选择一个合适的实例。  
| 策略名称                           | 原理                        | 特点和适用场景             |
| ------------------------------ | ------------------------- | ------------------- |
| **随机（Random）**                 | 从可用节点中随机选一个               | 简单高效，适合节点性能相近       |
| **轮询（Round Robin）**            | 顺序循环选择下一个节点               | 平均分配，适合无状态服务        |
| **加权轮询（Weighted Round Robin）** | 根据权重分配请求数量（性能越好，权重越高）     | 节点性能差异大             |
| **最少连接（Least Connections）**    | 选择当前处理请求数量最少的节点           | 节点请求数不均，适合长连接       |
| **一致性哈希（Consistent Hash）**     | 相同参数哈希后总是落在同一节点（如 userId） | 保证请求稳定性，适合缓存、会话绑定场景 |


