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
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.jgroups.commandhandling.JChannelFactory;
import org.axonframework.jgroups.commandhandling.JGroupsConnector;
import org.axonframework.jgroups.commandhandling.JGroupsXmlConfigurationChannelFactory;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.jgroups.JChannel;
import org.jgroups.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.context.ApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.isNull;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({JGroupsConnectorFactoryBean.class, JChannel.class, JGroupsConnector.class,
                        JGroupsXmlConfigurationChannelFactory.class, Util.class})
public class JGroupsConnectorFactoryBeanTest {

    private JGroupsConnectorFactoryBean testSubject;
    private ApplicationContext mockApplicationContext;
    private JChannel mockChannel;
    private JGroupsConnector mockConnector;

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(Util.class);
        mockApplicationContext = mock(ApplicationContext.class);
        mockChannel = mock(JChannel.class);
        mockConnector = mock(JGroupsConnector.class);
        when(mockApplicationContext.getBean(Serializer.class)).thenReturn(new XStreamSerializer());
        PowerMockito.whenNew(JChannel.class).withParameterTypes(String.class).withArguments(isA(String.class))
                .thenReturn(mockChannel);
        PowerMockito.whenNew(JGroupsConnector.class)
                .withArguments(
                        isA(CommandBus.class),
                        isA(JChannel.class),
                        isA(String.class),
                        isA(Serializer.class),
                        isA(RoutingStrategy.class))
                .thenReturn(mockConnector);

        testSubject = new JGroupsConnectorFactoryBean();
        testSubject.setBeanName("beanName");
        testSubject.setApplicationContext(mockApplicationContext);
    }

    @Test
    public void testCreateWithDefaultValues() throws Exception {
        testSubject.afterPropertiesSet();
        testSubject.start();
        testSubject.getObject();

        PowerMockito.verifyNew(JChannel.class).withArguments("tcp_mcast.xml");
        PowerMockito.verifyNew(JGroupsConnector.class).withArguments(
                isA(SimpleCommandBus.class),
                eq(mockChannel),
                eq("beanName"),
                isA(Serializer.class),
                isA(RoutingStrategy.class));
        verify(mockConnector).connect();
        verify(mockChannel, never()).close();

        PowerMockito.verifyStatic(never());
        Util.registerChannel(any(JChannel.class), anyString());

        testSubject.stop(() -> {
        });

        verify(mockChannel).close();
    }

    @Test
    public void testCreateWithSpecifiedValues() throws Exception {
        testSubject.setClusterName("ClusterName");
        testSubject.setConfiguration("custom.xml");
        XStreamSerializer serializer = new XStreamSerializer();
        testSubject.setSerializer(serializer);
        SimpleCommandBus localSegment = new SimpleCommandBus();
        testSubject.setLocalSegment(localSegment);
        RoutingStrategy routingStrategy = CommandMessage::getCommandName;
        testSubject.setRoutingStrategy(routingStrategy);
        testSubject.setChannelName("localname");
        testSubject.afterPropertiesSet();
        testSubject.start();
        testSubject.getObject();

        PowerMockito.verifyNew(JChannel.class).withArguments("custom.xml");
        PowerMockito.verifyNew(JGroupsConnector.class).withArguments(
                same(localSegment),
                eq(mockChannel),
                eq("ClusterName"),
                same(serializer),
                same(routingStrategy));
        verify(mockApplicationContext, never()).getBean(Serializer.class);
        verify(mockChannel).setName("localname");
        verify(mockConnector).connect();
        verify(mockChannel, never()).close();

        testSubject.stop(new Runnable() {
            @Override
            public void run() {
            }
        });

        verify(mockChannel).close();
    }

    @Test
    public void testCreateWithCustomChannel() throws Exception {
        JChannelFactory mockFactory = mock(JChannelFactory.class);
        when(mockFactory.createChannel()).thenReturn(mockChannel);

        testSubject.setChannelFactory(mockFactory);
        testSubject.afterPropertiesSet();
        testSubject.start();
        testSubject.getObject();

        verify(mockFactory).createChannel();
        PowerMockito.verifyNew(JGroupsConnector.class).withArguments(
                isA(SimpleCommandBus.class),
                eq(mockChannel),
                eq("beanName"),
                isA(Serializer.class),
                isA(RoutingStrategy.class));
        verify(mockConnector).connect();
        verify(mockChannel, never()).close();

        testSubject.stop(new Runnable() {
            @Override
            public void run() {
            }
        });

        verify(mockChannel).close();
    }

    @Test
    public void testRegisterMBean() throws Exception {

        testSubject.setRegisterMBean(true);
        testSubject.afterPropertiesSet();
        testSubject.start();
        testSubject.getObject();

        PowerMockito.verifyStatic(times(1));
        Util.registerChannel(eq(mockChannel), isNull(String.class));

        PowerMockito.verifyNew(JGroupsConnector.class).withArguments(
                isA(SimpleCommandBus.class),
                eq(mockChannel),
                eq("beanName"),
                isA(Serializer.class),
                isA(RoutingStrategy.class));
        verify(mockConnector).connect();
        verify(mockChannel, never()).close();

        testSubject.stop(() -> {
        });

        verify(mockChannel).close();
    }

    @Test
    public void testSimpleProperties() {
        assertEquals(Integer.MAX_VALUE, testSubject.getPhase());
        testSubject.setPhase(100);
        assertEquals(100, testSubject.getPhase());
        assertTrue(testSubject.isAutoStartup());
    }
}
