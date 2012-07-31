/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.Collections;
import java.util.Map;

/**
 * FactoryBean that creates an instance of a SimpleCluster, allowing easier access to the MetaData. By default, the
 * bean name is set as the Cluster name (<code>ClusterName</code> property).
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SimpleClusterFactoryBean implements FactoryBean<Cluster>, InitializingBean, BeanNameAware {

    private SimpleCluster cluster;
    private Map<String, Object> metaDataValues = Collections.emptyMap();
    private String beanName;

    @Override
    public Cluster getObject() throws Exception {
        return cluster;
    }

    @Override
    public Class<?> getObjectType() {
        return Cluster.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.cluster = new SimpleCluster();
        cluster.getMetaData().setProperty("ClusterName", beanName);
        for (Map.Entry<String, Object> entry : metaDataValues.entrySet()) {
            cluster.getMetaData().setProperty(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Sets the given <code>metaDataValues</code> in the Cluster.
     *
     * @param metaDataValues The Meta Data values to set
     */
    public void setMetaDataValues(Map<String, Object> metaDataValues) {
        this.metaDataValues = metaDataValues;
    }

    @Override
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }
}
