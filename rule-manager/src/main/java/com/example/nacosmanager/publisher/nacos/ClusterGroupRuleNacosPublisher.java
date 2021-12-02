package com.example.nacosmanager.publisher.nacos;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.nacos.api.config.ConfigService;
import com.example.nacosmanager.entity.ClusterGroupEntity;
import com.example.nacosmanager.publisher.DynamicRulePublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("clusterGroupRuleNacosPublisher")
public class ClusterGroupRuleNacosPublisher implements DynamicRulePublisher<List<ClusterGroupEntity>> {

    @Autowired
    private ConfigService configService;

    @Autowired
    private Converter<List<ClusterGroupEntity>, String> converter;

    @Override
    public void publish(String dataId, String groupId, List<ClusterGroupEntity> rules) throws Exception {
        AssertUtil.notEmpty(dataId, "dataId cannot be empty");
        AssertUtil.notEmpty(groupId, "groupId cannot be empty");
        if (rules == null) {
            return;
        }
        configService.publishConfig(dataId, groupId, converter.convert(rules));
    }
}
