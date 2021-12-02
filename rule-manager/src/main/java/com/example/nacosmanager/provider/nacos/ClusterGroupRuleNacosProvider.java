package com.example.nacosmanager.provider.nacos;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.nacos.api.config.ConfigService;
import com.example.nacosmanager.entity.ClusterGroupEntity;
import com.example.nacosmanager.provider.DynamicRuleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("clusterGroupRuleNacosProvider")
public class ClusterGroupRuleNacosProvider implements DynamicRuleProvider<List<ClusterGroupEntity>> {

    @Autowired
    private ConfigService configService;

    @Autowired
    private Converter<String, List<ClusterGroupEntity>> converter;

    @Override
    public List<ClusterGroupEntity> getRules(String dataId, String groupId) throws Exception {

        String rules = configService.getConfig(dataId, groupId, 3000);
        if (StringUtil.isEmpty(rules)) {
            return new ArrayList<>();
        }
        return converter.convert(rules);
    }
}
