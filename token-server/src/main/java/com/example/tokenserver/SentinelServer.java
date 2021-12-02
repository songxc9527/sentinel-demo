package com.example.tokenserver;

import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterParamFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.server.ClusterTokenServer;
import com.alibaba.csp.sentinel.cluster.server.SentinelDefaultTokenServer;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.util.HostNameUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.example.nacosmanager.constant.NacosCommonConstants;
import com.example.nacosmanager.entity.ClusterGroupEntity;
import com.example.nacosmanager.provider.DynamicRuleProvider;
import com.example.nacosmanager.publisher.DynamicRulePublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * sentinel server
 * @author songxincong
 */
@Slf4j
@Component
public class SentinelServer implements DisposableBean {

    @Value("${nacos.config.server-addr}")
    private String remoteAddress;

    @Value("${nacos.config.namespace}")
    private String namespace;

    @Value("${nacos.config.group}")
    private String groupId;

    @Value("${zookeeper.address}")
    private String zkAddress;

    @Autowired
    @Qualifier("clusterGroupRuleNacosPublisher")
    private DynamicRulePublisher<List<ClusterGroupEntity>> publisher;

    @Autowired
    @Qualifier("clusterGroupRuleNacosProvider")
    private DynamicRuleProvider<List<ClusterGroupEntity>> provider;

    /**
     * nacos properties
     */
    private Properties properties;

    /**
     * server leader latch client
     */
    private ServerLeaderClient serverLeaderClient;

    private static final String namespaceSetDataId = "cluster-server-namespace-set";
    private static final String serverTransportDataId = "cluster-server-transport-config";

    /**
     * token-server-leader zk path
     */
    private static final String LEADER_LATCH_PATH = "/crm/sentinel/token-server-leader";

    @PostConstruct
    public void init() throws Exception {
        initProperties();
        initPropertySupplier();
        initNamespaceSetProperty();
        // can load from nacos
//        initServerTransportConfigProperty();
        runTokenServer();
    }

    /**
     * init nacos properties, use for getting configService
     */
    private void initProperties() {
        this.properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, remoteAddress);
        properties.put(PropertyKeyConst.NAMESPACE, namespace);
    }

    private void runTokenServer() throws Exception {
        // Not embedded mode by default (alone mode).
        ClusterTokenServer tokenServer = new SentinelDefaultTokenServer();

        // set port
        ServerTransportConfig serverTransportConfig = new ServerTransportConfig();
        serverTransportConfig.setPort(11112);
        ClusterServerConfigManager.loadGlobalTransportConfig(serverTransportConfig);

        // Start the server.
        tokenServer.start();

        // init serverLeaderClient
        serverLeaderClient = new ServerLeaderClient(zkAddress, LEADER_LATCH_PATH, groupId, publisher, provider);

        // latch leader
        serverLeaderClient.latch();
    }

    private void initPropertySupplier() {
        // Register cluster flow rule property supplier which creates data source by namespace.
        ClusterFlowRuleManager.setPropertySupplier(namespace -> {
            ReadableDataSource<String, List<FlowRule>> ds = new NacosDataSource<>(properties, groupId,
                    namespace + NacosCommonConstants.APP_FLOW_RULES_SUFFIX,
                    source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
            return ds.getProperty();
        });
        // Register cluster parameter flow rule property supplier.
        ClusterParamFlowRuleManager.setPropertySupplier(namespace -> {
            ReadableDataSource<String, List<ParamFlowRule>> ds = new NacosDataSource<>(properties, groupId,
                    namespace + NacosCommonConstants.APP_PARAM_FLOW_POSTFIX,
                    source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>() {}));
            return ds.getProperty();
        });
    }

    private void initNamespaceSetProperty() {
        // Server namespace set (scope) data source.
        ReadableDataSource<String, Set<String>> namespaceDs = new NacosDataSource<>(properties, groupId,
                namespaceSetDataId, source -> JSON.parseObject(source, new TypeReference<Set<String>>() {}));
        ClusterServerConfigManager.registerNamespaceSetProperty(namespaceDs.getProperty());
    }

    private void initServerTransportConfigProperty() {

        // Server transport configuration data source.
        ReadableDataSource<String, ServerTransportConfig> transportConfigDs = new NacosDataSource<>(properties,
                groupId, serverTransportDataId,
                source -> JSON.parseObject(source, new TypeReference<ServerTransportConfig>() {}));
        ClusterServerConfigManager.registerServerTransportProperty(transportConfigDs.getProperty());
    }

    @Override
    public void destroy() throws Exception {
        CloseableUtils.closeQuietly(serverLeaderClient);
    }

    static class ServerLeaderClient implements Closeable {

        private final String name;

        private final String groupId;

        private CuratorFramework curator;

        private final LeaderLatch leaderLatch;

        private final DynamicRulePublisher<List<ClusterGroupEntity>> publisher;

        private final DynamicRuleProvider<List<ClusterGroupEntity>> provider;

        public ServerLeaderClient(String address, String path, String groupId,
                                  DynamicRulePublisher<List<ClusterGroupEntity>> publisher,
                                  DynamicRuleProvider<List<ClusterGroupEntity>> provider) {
            this.name = HostNameUtil.getIp() + ":" + ClusterServerConfigManager.getPort();
            buildZkClient(address);
            this.groupId = groupId;
            this.leaderLatch = new LeaderLatch(curator, path, name);
            addListener();
            this.publisher = publisher;
            this.provider = provider;
        }

        /**
         * add LeaderLatchListener
         */
        private void addListener() {
            this.leaderLatch.addListener(new LeaderLatchListener() {
                @Override
                public void isLeader() {
                    log.info("[{} become to leader]", name);
                    checkLeader();
                }

                @Override
                public void notLeader() {
                    log.info("[{} lost leader]", name);
                }
            });
        }

        /**
         * init zk client
         */
        private void buildZkClient(String address) {
            this.curator = CuratorFrameworkFactory.builder()
                    .connectString(address)
                    .sessionTimeoutMs(100000)
                    .connectionTimeoutMs(100000)
                    .retryPolicy(new ExponentialBackoffRetry(1000, 10))
                    .build();
            curator.start();
        }

        @Override
        public void close() throws IOException {
            leaderLatch.close(LeaderLatch.CloseMode.NOTIFY_LEADER);
            curator.close();
        }

        /**
         * to latch leader
         */
        public void latch() {
            try {
                leaderLatch.start();
            } catch (Exception e) {
                log.error("[name {}] latch error", name, e);
            }
        }

        /**
         * check current node
         * if current node is leader, then publish config
         */
        private void checkLeader() {
            try {
                Set<String> namespaceSet = ClusterServerConfigManager.getNamespaceSet();
                if (CollectionUtils.isEmpty(namespaceSet)) {
                    return;
                }
                for (String app : namespaceSet) {
                    // get config from nacos
                    String dataId = app + NacosCommonConstants.APP_CLUSTER_MAP_SUFFIX;
                    List<ClusterGroupEntity> groups = this.provider.getRules(dataId, groupId);
                    if (!CollectionUtils.isEmpty(groups)) {
                        ClusterGroupEntity group = groups.get(0);
                        if (Objects.nonNull(group.getIp()) && Objects.nonNull(group.getPort())) {
                            String address = group.getIp() + ":" + group.getPort();
                            // if leader is current node return
                            if (name.equals(address)) {
                                return;
                            }
                        }
                    }

                    // publish current node to be leader
                    if (leaderLatch.hasLeadership()) {
                        ClusterGroupEntity group = new ClusterGroupEntity();
                        group.setIp(HostNameUtil.getIp());
                        group.setPort(ClusterServerConfigManager.getPort());
                        publisher.publish(dataId, groupId, Collections.singletonList(group));
                        log.info("check leader published dataId {} groupId {}", dataId, groupId);
                    }
                }

            } catch (Exception e) {
                log.error("check leader error", e);
            }
        }
    }
}