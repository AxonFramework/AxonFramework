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
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.jgroups.JChannel;
import org.jgroups.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.context.ApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.*;

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
    private HashChangeListener mockListener;

    @Before
    public void setUp() throws Exception {
        mockStatic(Util.class);
        mockApplicationContext = mock(ApplicationContext.class);
        mockChannel = mock(JChannel.class);
        mockConnector = mock(JGroupsConnector.class);
        mockListener = mock(HashChangeListener.class);
        when(mockApplicationContext.getBean(Serializer.class)).thenReturn(new XStreamSerializer());
        whenNew(JChannel.class).withParameterTypes(String.class).withArguments(isA(String.class))
                .thenReturn(mockChannel);
        whenNew(JGroupsConnector.class)
                .withArguments(isA(JChannel.class),
                               isA(String.class),
                               isA(CommandBus.class),
                               isA(Serializer.class),
                               anyObject() /*HashChangeListener or null */)
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

        verifyNew(JChannel.class).withArguments("tcp_mcast.xml");
        verifyNew(JGroupsConnector.class).withArguments(eq(mockChannel), eq("beanName"), isA(
                SimpleCommandBus.class), isA(Serializer.class), isNull());
        verify(mockConnector).connect(100);
        verify(mockChannel, never()).close();

        verifyStatic(never());
        Util.registerChannel(any(JChannel.class), anyString());
        
        testSubject.stop(() -> {
        });

        verify(mockChannel).close();
    }

    @Test
    public void testCreateWithSpecifiedValues() throws Exception {
        testSubject.setClusterName("ClusterName");
        testSubject.setConfiguration("custom.xml");
        testSubject.setLoadFactor(200);
        XStreamSerializer serializer = new XStreamSerializer();
        testSubject.setSerializer(serializer);
        SimpleCommandBus localSegment = new SimpleCommandBus();
        testSubject.setLocalSegment(localSegment);
        testSubject.setChannelName("localname");
        testSubject.setHashChangeListener(mockListener);
        testSubject.afterPropertiesSet();
        testSubject.start();
        testSubject.getObject();

        verifyNew(JChannel.class).withArguments("custom.xml");
        verifyNew(JGroupsConnector.class).withArguments(eq(mockChannel), eq("ClusterName"),
                                                        same(localSegment), same(serializer), same(mockListener));
        verify(mockApplicationContext, never()).getBean(Serializer.class);
        verify(mockChannel).setName("localname");
        verify(mockConnector).connect(200);
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
        verifyNew(JGroupsConnector.class).withArguments(eq(mockChannel), eq("beanName"), isA(
                SimpleCommandBus.class), isA(Serializer.class), isNull());
        verify(mockConnector).connect(100);
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

        verifyStatic(times(1));
        Util.registerChannel(eq(mockChannel), isNull(String.class));

        verifyNew(JGroupsConnector.class).withArguments(eq(mockChannel), eq("beanName"), isA(
                SimpleCommandBus.class), isA(Serializer.class), isNull());
        verify(mockConnector).connect(100);
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
