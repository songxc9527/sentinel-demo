/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.nacosmanager.provider;

/**
 * 动态规则获取
 * @author songxincong
 */
public interface DynamicRuleProvider<T> {

    /**
     * get dynamic rule
     * @param dataId nacos data-id
     * @param groupId nacos group
     * @return 动态规则
     * @throws Exception
     */
    T getRules(String dataId, String groupId) throws Exception;
}
