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
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.jgroups.JChannel;
import org.junit.*;
import org.junit.runner.*;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.context.ApplicationContext;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.verifyNew;
import static org.powermock.api.mockito.PowerMockito.whenNew;

/**
 * @author Allard Buijze
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({JGroupsConnectorFactoryBean.class, JChannel.class, JGroupsConnector.class})
public class JGroupsConnectorFactoryBeanTest {

    private JGroupsConnectorFactoryBean testSubject;
    private ApplicationContext mockApplicationContext;
    private JChannel mockChannel;
    private JGroupsConnector mockConnector;

    @Before
    public void setUp() throws Exception {
        mockApplicationContext = mock(ApplicationContext.class);
        mockChannel = mock(JChannel.class);
        mockConnector = mock(JGroupsConnector.class);
        when(mockApplicationContext.getBean(Serializer.class)).thenReturn(new XStreamSerializer());
        whenNew(JChannel.class).withParameterTypes(String.class).withArguments(isA(String.class))
                .thenReturn(mockChannel);
        whenNew(JGroupsConnector.class)
                .withArguments(isA(JChannel.class), isA(String.class), isA(CommandBus.class), isA(Serializer.class))
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
                SimpleCommandBus.class), isA(Serializer.class));
        verify(mockConnector).connect(100);
        verify(mockChannel, never()).close();

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
        testSubject.afterPropertiesSet();
        testSubject.start();
        testSubject.getObject();

        verifyNew(JChannel.class).withArguments("custom.xml");
        verifyNew(JGroupsConnector.class).withArguments(eq(mockChannel), eq("ClusterName"),
                                                        same(localSegment), same(serializer));
        verify(mockApplicationContext, never()).getBean(Serializer.class);
        verify(mockChannel).setName("localname");
        verify(mockConnector).connect(200);
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
