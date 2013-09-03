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

package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.serializer.Serializer;
import org.jgroups.JChannel;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.util.List;

/**
 * Spring Factory bean that creates a JGroupsConnector and starts it when the application context is started. This bean
 * must be defined as a top-level bean.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class JGroupsConnectorFactoryBean implements FactoryBean, InitializingBean, SmartLifecycle, BeanNameAware,
        ApplicationContextAware {

    private JGroupsConnector connector;
    private Serializer serializer;
    private String configuration = "tcp_mcast.xml";
    private String clusterName;
    private String channelName;
    private CommandBus localSegment;
    private int loadFactor = 100;
    private JChannel channel;
    private int phase = Integer.MAX_VALUE;
    private String beanName;
    private ApplicationContext applicationContext;
    private List<CommandHandlerInterceptor> interceptors;

    @Override
    public Object getObject() throws Exception {
        return connector;
    }

    @Override
    public Class<?> getObjectType() {
        return JGroupsConnector.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (localSegment == null) {
            SimpleCommandBus bus = new SimpleCommandBus();
            if (interceptors != null && !interceptors.isEmpty()) {
                bus.setHandlerInterceptors(interceptors);
            }
            localSegment = bus;
        }
        if (serializer == null) {
            serializer = applicationContext.getBean(Serializer.class);
        }
        if (clusterName == null) {
            clusterName = beanName;
        }
        channel = new JChannel(configuration);
        channel.setName(channelName);
        connector = new JGroupsConnector(channel, clusterName, localSegment, serializer);
    }

    /**
     * Sets the serializer used to serialize events before they are dispatched to the destination. All members
     * connected to the same channel must use compatible serializers.
     * <p/>
     * Default to an autowired Serializer.
     *
     * @param serializer the serializer to serialize commands with
     */
    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    /**
     * Sets the JGroups configuration file to load. Defaults to configuration using TCP with multicast discovery
     * (tcp_mcast.xml).
     *
     * @param configuration the JGroups configuration file
     */
    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    /**
     * Sets the name of the cluster to subscribe to. The Connector will only connect to other instances with the same
     * cluster name. Defaults to the bean name.
     *
     * @param clusterName The name of the cluster to connect to
     */
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    /**
     * Optionally sets the logical channel name of the channel. If not provided JGroups will generate a default name.
     *
     * @param channelName The logical name to give to the channel
     */
    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    /**
     * Sets the CommandBus instance on which local commands must be dispatched. Defaults to a SimpleCommandBus.
     *
     * @param localSegment the CommandBus instance to dispatch local messages on
     */
    public void setLocalSegment(CommandBus localSegment) {
        this.localSegment = localSegment;
    }

    /**
     * Sets the interceptor to use in the default local segment (a SimpleCommandBus). When providing a custom local
     * segment ({@link #setLocalSegment(org.axonframework.commandhandling.CommandBus)}), this configuration is ignored.
     *
     * @param interceptors the list of interceptors (in order) for the local segment
     */
    public void setInterceptors(List<CommandHandlerInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    /**
     * Sets the load factor for this instance of the Distributed Command Bus. This factor described the relative number
     * of Command messages this instance will handle. Defaults to 100.
     *
     * @param loadFactor The load factor for this instance
     */
    public void setLoadFactor(int loadFactor) {
        this.loadFactor = loadFactor;
    }

    @Override
    public void start() {
        try {
            connector.connect(loadFactor);
            connector.awaitJoined();
        } catch (Exception e) {
            throw new ConnectionFailedException("Could not start JGroups Connector", e);
        }
    }

    @Override
    public void stop() {
        channel.close();
    }

    @Override
    public boolean isRunning() {
        return channel.isConnected();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        channel.close();
        callback.run();
    }

    @Override
    public int getPhase() {
        // the connector must start last. All other components must be started before participating in the
        // distributed command bus
        return phase;
    }

    /**
     * Sets the phase in which this bean must be started. Defaults to {@code Integer.MAX_VALUE}, which ensures the
     * Connector is started when other beans have been initialized.
     *
     * @param phase the phase in which the JGroups connector must be started.
     * @see SmartLifecycle
     */
    public void setPhase(int phase) {
        this.phase = phase;
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
