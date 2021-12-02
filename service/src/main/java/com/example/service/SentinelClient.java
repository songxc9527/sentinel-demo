package com.example.service;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.transport.config.TransportConfig;
import com.alibaba.csp.sentinel.util.HostNameUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.example.service.entity.ClusterGroupEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

@Slf4j
@Component
public class SentinelClient {

    @Value("${nacos.config.server-addr}")
    private String remoteAddress;

    @Value("${nacos.config.namespace}")
    private String namespace;

    @Value("${nacos.config.group}")
    private String groupId;

    @Value("${nacos.config.data-id}")
    private String dataId;

    private Properties properties;

    private static final String clientConfigDataId = "appA-cluster-client-config";

    private static final String clusterMapDataId = "appA-cluster-map";

    private static final String SEPARATOR = "@";

    @PostConstruct
    public void init() {
        initProperties();
        initClientConfigProperty();
        initClientServerAssignProperty();
        initClusterState();
        initClientRuleProperty();
    }

    /**
     * 初始化Nacos properties 用于获取configService
     */
    private void initProperties() {
        this.properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, remoteAddress);
        properties.put(PropertyKeyConst.NAMESPACE, namespace);
    }

    private void initClusterState() {
        // 指定当前身份为 Token Client
        ClusterStateManager.applyState(ClusterStateManager.CLUSTER_CLIENT);
    }

    private void initClientRuleProperty() {
        // 使用 Nacos 数据源作为配置中心，需要在 REMOTE_ADDRESS 上启动一个 Nacos 的服务
        ReadableDataSource<String, List<FlowRule>> ds =
                new NacosDataSource<>(properties, groupId, dataId,
                        source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
        // 为集群客户端注册动态规则源
        FlowRuleManager.register2Property(ds.getProperty());
    }

    private void initClientConfigProperty() {
        // 初始化一个配置ClusterClientConfig的 Nacos 数据源
        ReadableDataSource<String, ClusterClientConfig> ds =
                new NacosDataSource<>(properties, groupId, clientConfigDataId,
                        source -> JSON.parseObject(source, new TypeReference<ClusterClientConfig>() {}));
        ClusterClientConfigManager.registerClientConfigProperty(ds.getProperty());
    }

    private void initClientServerAssignProperty() {
        // Cluster map format:
        // [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
        // machineId: <ip@commandPort>, commandPort for port exposed to Sentinel dashboard (transport module)
        ReadableDataSource<String, ClusterClientAssignConfig> clientAssignDs = new NacosDataSource<>(properties, groupId,
                clusterMapDataId, source -> {
            List<ClusterGroupEntity> groupList = JSON.parseObject(source, new TypeReference<List<ClusterGroupEntity>>() {});
            return Optional.ofNullable(groupList)
                    .flatMap(this::extractClientAssignment)
                    .orElse(null);
        });
        ClusterClientConfigManager.registerServerAssignProperty(clientAssignDs.getProperty());
    }

    private Optional<ClusterClientAssignConfig> extractClientAssignment(List<ClusterGroupEntity> groupList) {

        if (CollectionUtils.isEmpty(groupList)) {
            return Optional.empty();
        }
        ClusterGroupEntity group = groupList.get(0);
        return Optional.of(new ClusterClientAssignConfig(group.getIp(), group.getPort()));
//        if (groupList.stream().anyMatch(this::machineEqual)) {
//            return Optional.empty();
//        }
//        // Build client assign config from the client set of target server group.
//        for (ClusterGroupEntity group : groupList) {
//            if (group.getClientSet().contains(getCurrentMachineId())) {
//                String ip = group.getIp();
//                Integer port = group.getPort();
//                return Optional.of(new ClusterClientAssignConfig(ip, port));
//            }
//        }
//        return Optional.empty();
    }

    private boolean machineEqual(/*@Valid*/ ClusterGroupEntity group) {
        return getCurrentMachineId().equals(group.getMachineId());
    }

    private String getCurrentMachineId() {
        // Note: this may not work well for container-based env.
        return HostNameUtil.getIp() + SEPARATOR + TransportConfig.getRuntimePort();
    }
}
