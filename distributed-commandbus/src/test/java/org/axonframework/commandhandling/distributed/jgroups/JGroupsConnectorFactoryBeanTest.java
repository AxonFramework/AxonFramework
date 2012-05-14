package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.monitoring.jmx.JmxConfiguration;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.jgroups.JChannel;
import org.junit.*;
import org.junit.runner.*;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.context.ApplicationContext;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.same;
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
        JmxConfiguration.getInstance().disableMonitoring();
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
        verify(mockChannel).connect("beanName");
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
    public void testCreateWithSpecifiedValues() throws Exception {
        testSubject.setClusterName("ClusterName");
        testSubject.setConfiguration("custom.xml");
        testSubject.setLoadFactor(200);
        XStreamSerializer serializer = new XStreamSerializer();
        testSubject.setSerializer(serializer);
        SimpleCommandBus localSegment = new SimpleCommandBus(false);
        testSubject.setLocalSegment(localSegment);
        testSubject.afterPropertiesSet();
        testSubject.start();
        testSubject.getObject();

        verifyNew(JChannel.class).withArguments("custom.xml");
        verifyNew(JGroupsConnector.class).withArguments(eq(mockChannel), eq("ClusterName"),
                                                        same(localSegment), same(serializer));
        verify(mockApplicationContext, never()).getBean(Serializer.class);
        verify(mockChannel).connect("ClusterName");
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
    public void testSimpleProperties() {
        assertEquals(Integer.MAX_VALUE, testSubject.getPhase());
        testSubject.setPhase(100);
        assertEquals(100, testSubject.getPhase());
        assertTrue(testSubject.isAutoStartup());
    }
}
