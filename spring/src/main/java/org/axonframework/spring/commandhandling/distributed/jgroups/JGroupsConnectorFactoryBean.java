/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.spring.commandhandling.distributed.jgroups;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.jgroups.commandhandling.ConnectionFailedException;
import org.axonframework.jgroups.commandhandling.JChannelFactory;
import org.axonframework.jgroups.commandhandling.JGroupsConnector;
import org.axonframework.jgroups.commandhandling.JGroupsXmlConfigurationChannelFactory;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.serialization.Serializer;
import org.jgroups.JChannel;
import org.jgroups.util.Util;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spring Factory bean that creates a JGroupsConnector and starts it when the application context is started. This bean
 * must be defined as a top-level bean.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class JGroupsConnectorFactoryBean implements FactoryBean<JGroupsConnector>, InitializingBean, SmartLifecycle,
        BeanNameAware, ApplicationContextAware {

    private JGroupsConnector connector;
    private JChannelFactory channelFactory = new JGroupsXmlConfigurationChannelFactory("tcp_mcast.xml");
    private Serializer serializer;
    private String clusterName;
    private String channelName;
    private CommandBus localSegment;
    private JChannel channel;
    private int phase = Integer.MAX_VALUE;
    private String beanName;
    private ApplicationContext applicationContext;
    private List<MessageHandlerInterceptor<CommandMessage<?>>> interceptors;
    private long joinTimeout = -1;
    private boolean registerMBean = false;
    private RoutingStrategy routingStrategy = new AnnotationRoutingStrategy();

    @Override
    public JGroupsConnector getObject() throws Exception {
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
            if (interceptors != null) {
                interceptors.forEach(bus::registerHandlerInterceptor);
            }
            localSegment = bus;
        }
        if (serializer == null) {
            serializer = applicationContext.getBean(Serializer.class);
        }
        if (clusterName == null) {
            clusterName = beanName;
        }
        channel = channelFactory.createChannel();
        if (channelName != null) {
            channel.setName(channelName);
        }
        connector = new JGroupsConnector(localSegment, channel, clusterName, serializer, routingStrategy);
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
     * (tcp_mcast.xml). Alternatively the JChannel can be instantiated programatically by setting the
     * {@link #setChannelFactory JChannelFactory}.
     *
     * @param configuration the JGroups configuration file
     */
    public void setConfiguration(String configuration) {
        this.channelFactory = new JGroupsXmlConfigurationChannelFactory(configuration);
    }

    /**
     * Sets the {@link RoutingStrategy} that the JGroupsConnector will use to determine to which endpoint a message
     * should be routed.
     *
     * @param routingStrategy the routing strategy of the connector
     */
    public void setRoutingStrategy(RoutingStrategy routingStrategy) {
        this.routingStrategy = routingStrategy;
    }

    /**
     * Sets the JChannelFactory that allows programmatic definition of the JChannel.
     *
     * @param channelFactory The factory that creates the JChannel
     */
    public void setChannelFactory(JChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
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
     * <p/>
     * Note that each member of a cluster should have a unique channel name.
     *
     * @param channelName The logical name to give to the channel
     */
    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    /**
     * Sets the number of milliseconds to wait for this member to join the group. Setting this to a non-negative number
     * will cause the start() method to block for at most the given number of milliseconds. After that timeout,
     * attempts to join the cluster will be performed on the background.
     * <p/>
     * Setting a negative value will cause the {@link #start()} method to wait until the member joined the cluster.
     * <p/>
     * Defaults to -1, which causes the {@link #start()} to wait indefinitely, until the member has joined the cluster.
     *
     * @param joinTimeout The number of milliseconds to wait for the member to join the cluster
     */
    public void setJoinTimeout(long joinTimeout) {
        this.joinTimeout = joinTimeout;
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
    public void setInterceptors(List<MessageHandlerInterceptor<CommandMessage<?>>> interceptors) {
        this.interceptors = interceptors;
    }

    /**
     * Registers the JChannel monitoring bean after the channel has connected. Defaults to false.
     *
     * @param registerMBean if {@code true} the JGroups channel will be registered with the MBeanServer
     */
    public void setRegisterMBean(boolean registerMBean) {
        this.registerMBean = registerMBean;
    }

    @Override
    public void start() {
        try {
            connector.connect();
            if (joinTimeout >= 0) {
                connector.awaitJoined(joinTimeout, TimeUnit.MILLISECONDS);
            } else {
                connector.awaitJoined();
            }
            if (registerMBean) {
                Util.registerChannel(channel, null);
            }
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
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}
