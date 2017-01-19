package org.axonframework.springcloud.commandhandling;

import com.google.common.collect.ImmutableList;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.distributed.ConsistentHash;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.SimpleMember;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SpringCloudCommandRouterTest {

    private static final String LOAD_FACTOR_KEY = "loadFactor";
    private static final String SERIALIZED_COMMAND_FILTER_KEY = "serializedCommandFilter";
    private static final String SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY = "serializedCommandFilterClassName";

    private static final int LOAD_FACTOR = 1;
    private static final CommandMessage<Object> TEST_COMMAND = GenericCommandMessage.asCommandMessage("testCommand");
    private static final String ROUTING_KEY = "routingKey";
    private static final String SERVICE_INSTANCE_ID = "SERVICEID";
    private static final URI SERVICE_INSTANCE_URI = URI.create("endpoint");
    private static final CommandNameFilter COMMAND_NAME_FILTER = new CommandNameFilter(String.class.getName());

    @InjectMocks
    private SpringCloudCommandRouter testSubject;

    @Mock
    private DiscoveryClient discoveryClient;
    @Mock
    private RoutingStrategy routingStrategy;
    private Field atomicConsistentHashField;

    private String serializedCommandFilterData;
    private String serializedCommandFilterClassName;
    private HashMap<String, String> serviceInstanceMetadata;
    @Mock
    private ServiceInstance serviceInstance;

    @Before
    public void setUp() throws Exception {
        serializedCommandFilterData = new XStreamSerializer().serialize(COMMAND_NAME_FILTER, String.class).getData();
        serializedCommandFilterClassName = COMMAND_NAME_FILTER.getClass().getName();

        String atomicConsistentHashFieldName = "atomicConsistentHash";
        atomicConsistentHashField = SpringCloudCommandRouter.class.getDeclaredField(atomicConsistentHashFieldName);

        serviceInstanceMetadata = new HashMap<>();
        when(serviceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(serviceInstance.getUri()).thenReturn(SERVICE_INSTANCE_URI);
        when(serviceInstance.getMetadata()).thenReturn(serviceInstanceMetadata);

        when(discoveryClient.getLocalServiceInstance()).thenReturn(serviceInstance);
        when(discoveryClient.getServices()).thenReturn(Collections.singletonList(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID)).thenReturn(Collections.singletonList(serviceInstance));

        when(routingStrategy.getRoutingKey(any())).thenReturn(ROUTING_KEY);
    }

    @Test
    public void testFindDestinationReturnsEmptyOptionalMemberForCommandMessage() throws Exception {
        Optional<Member> result = testSubject.findDestination(TEST_COMMAND);

        assertFalse(result.isPresent());
        verify(routingStrategy).getRoutingKey(TEST_COMMAND);
    }

    @Test
    public void testFindDestinationReturnsMemberForCommandMessage() throws Exception {
        SimpleMember<URI> testMember = new SimpleMember<>(SERVICE_INSTANCE_ID, SERVICE_INSTANCE_URI, null);
        AtomicReference<ConsistentHash> testAtomicConsistentHash =
                new AtomicReference<>(new ConsistentHash().with(testMember, LOAD_FACTOR, commandMessage -> true));
        ReflectionUtils.setFieldValue(atomicConsistentHashField, testSubject, testAtomicConsistentHash);

        Optional<Member> resultOptional = testSubject.findDestination(TEST_COMMAND);

        assertTrue(resultOptional.isPresent());
        Member resultMember = resultOptional.orElseThrow(IllegalStateException::new);

        assertMember(SERVICE_INSTANCE_ID, SERVICE_INSTANCE_URI, resultMember);

        verify(routingStrategy).getRoutingKey(TEST_COMMAND);
    }

    @Test
    public void testUpdateMembershipUpdatesLocalServiceInstance() throws Exception {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        assertEquals(Integer.toString(LOAD_FACTOR), serviceInstanceMetadata.get(LOAD_FACTOR_KEY));
        assertEquals(serializedCommandFilterData, serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER_KEY));
        assertEquals(serializedCommandFilterClassName, serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY));

        verify(discoveryClient).getLocalServiceInstance();
    }

    @Test
    public void testUpdateMemberShipUpdatesConsistentHash() throws Exception {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        AtomicReference<ConsistentHash> resultAtomicConsistentHash =
                ReflectionUtils.getFieldValue(atomicConsistentHashField, testSubject);

        Set<Member> resultMemberSet = resultAtomicConsistentHash.get().getMembers();
        assertFalse(resultMemberSet.isEmpty());

        assertMember(SERVICE_INSTANCE_ID, SERVICE_INSTANCE_URI, resultMemberSet.iterator().next());

        verify(discoveryClient).getLocalServiceInstance();
    }

    @Test
    public void testUpdateMembershipsOnHeartbeatEventUpdatesConsistentHash() throws Exception {
        serviceInstanceMetadata.put(LOAD_FACTOR_KEY, Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_KEY, serializedCommandFilterData);
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY, serializedCommandFilterClassName);

        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        AtomicReference<ConsistentHash> resultAtomicConsistentHash =
                ReflectionUtils.getFieldValue(atomicConsistentHashField, testSubject);

        Set<Member> resultMemberSet = resultAtomicConsistentHash.get().getMembers();
        assertFalse(resultMemberSet.isEmpty());

        assertMember(SERVICE_INSTANCE_ID, SERVICE_INSTANCE_URI, resultMemberSet.iterator().next());

        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
    }

    @Test
    public void testUpdateMembershipsOnHeartbeatEventFiltersInstancesWithoutCommandRouterSpecificMetadata() throws Exception {
        int expectedMemberSetSize = 1;
        String expectedServiceInstanceId = "nonCommandRouterServiceInstance";

        serviceInstanceMetadata.put(LOAD_FACTOR_KEY, Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_KEY, serializedCommandFilterData);
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY, serializedCommandFilterClassName);

        ServiceInstance nonCommandRouterServiceInstance = mock(ServiceInstance.class);
        when(nonCommandRouterServiceInstance.getServiceId()).thenReturn(expectedServiceInstanceId);

        when(discoveryClient.getServices())
                .thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID, expectedServiceInstanceId));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID))
                .thenReturn(ImmutableList.of(serviceInstance, nonCommandRouterServiceInstance));

        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        AtomicReference<ConsistentHash> resultAtomicConsistentHash =
                ReflectionUtils.getFieldValue(atomicConsistentHashField, testSubject);

        Set<Member> resultMemberSet = resultAtomicConsistentHash.get().getMembers();
        assertEquals(expectedMemberSetSize, resultMemberSet.size());

        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
        verify(discoveryClient).getInstances(expectedServiceInstanceId);
    }

    private void assertMember(String expectedMemberName, URI expectedEndpoint, Member resultMember) {
        assertEquals(resultMember.getClass(), ConsistentHash.ConsistentHashMember.class);
        ConsistentHash.ConsistentHashMember result = (ConsistentHash.ConsistentHashMember) resultMember;
        assertEquals(result.name(), expectedMemberName);
        assertEquals(result.segmentCount(), LOAD_FACTOR);

        Optional<URI> connectionEndpointOptional = result.getConnectionEndpoint(URI.class);
        assertTrue(connectionEndpointOptional.isPresent());
        URI resultEndpoint = connectionEndpointOptional.orElseThrow(IllegalStateException::new);
        assertEquals(resultEndpoint, expectedEndpoint);
    }

}
