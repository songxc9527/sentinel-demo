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
package com.example.nacosmanager.publisher;

/**
 * 动态规则发布
 * @author songxincong
 */
public interface DynamicRulePublisher<T> {

    /**
     * Publish rules to remote rule configuration center for given application name.
     *
     * @param dataId nacos data-id
     * @param groupId nacos group
     * @param rules list of rules to push
     * @throws Exception if some error occurs
     */
    void publish(String dataId, String groupId, T rules) throws Exception;
}
