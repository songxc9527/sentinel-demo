# sentinel-demo

### 项目介绍
springboot集成sentinel作集群流控，使用nacos作为数据源，sentinel-dashboard修改流控规则同步到nacos，应用监听到流控规则变更进行同步，采用独立模式部署token-server，并使用zookeeper做高可用。

### 架构
![image](https://github.com/songxc9527/sentinel-demo/blob/master/basic/sentinel.png)

### 环境
|  工具   | 版本  |
|  ----  | ----  |
| JDK  | 1.8 |
| springboot  | 1.5.9.RELEASE |
| sentinel  | 1.8.2 |
| nacos  | 1.4.2 |
| zookeeper  | 3.4.8 |
| curator  | 4.1.0 |

建议根据自己项目中的工具版本进行改造
