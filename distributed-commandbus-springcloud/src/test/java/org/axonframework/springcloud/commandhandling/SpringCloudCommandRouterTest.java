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
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.junit.Assert;
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

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SpringCloudCommandRouterTest {

    private static final int LOAD_FACTOR = 1;
    private static final CommandMessage<Object> TEST_COMMAND = GenericCommandMessage.asCommandMessage("testCommand");
    private static final String ROUTING_KEY = "routingKey";
    private static final String SERVICE_INSTANCE_ID = "SERVICEID";
    private static final URI SERVICE_INSTANCE_URI = URI.create("endpoint");
    private static final CommandNameFilter COMMAND_NAME_FILTER = new CommandNameFilter(String.class.getName());
    private static final String SERIALIZED_COMMAND_FILTER = "dummyCommandFilterData";
    private static final String SERIALIZED_COMMAND_FILTER_CLASS_NAME = String.class.getName();

    @InjectMocks
    private SpringCloudCommandRouter testSubject;
    @Mock
    private DiscoveryClient discoveryClient;
    @Mock
    private RoutingStrategy routingStrategy;
    @Mock
    private Serializer serializer;

    private Field consistentHashField;
    @Mock
    private SerializedObject<String> serializedObject;
    private HashMap<String, String> serviceInstanceMetadata;
    @Mock
    private ServiceInstance serviceInstance;
    private SimpleSerializedObject<String> expectedSerializedObject;

    @Before
    public void setUp() throws Exception {
        String consistentHashFieldName = "consistentHash";
        consistentHashField = SpringCloudCommandRouter.class.getDeclaredField(consistentHashFieldName);

        SerializedType serializedType = mock(SerializedType.class);
        when(serializedType.getName()).thenReturn(SERIALIZED_COMMAND_FILTER_CLASS_NAME);
        when(serializedObject.getType()).thenReturn(serializedType);
        when(serializedObject.getData()).thenReturn(SERIALIZED_COMMAND_FILTER);

        serviceInstanceMetadata = new HashMap<>();
        when(serviceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(serviceInstance.getUri()).thenReturn(SERVICE_INSTANCE_URI);
        when(serviceInstance.getMetadata()).thenReturn(serviceInstanceMetadata);

        expectedSerializedObject = new SimpleSerializedObject<>(
                SERIALIZED_COMMAND_FILTER, String.class, SERIALIZED_COMMAND_FILTER_CLASS_NAME, null);

        when(discoveryClient.getLocalServiceInstance()).thenReturn(serviceInstance);
        when(discoveryClient.getServices()).thenReturn(Collections.singletonList(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID)).thenReturn(Collections.singletonList(serviceInstance));

        when(routingStrategy.getRoutingKey(any())).thenReturn(ROUTING_KEY);

        when(serializer.serialize(COMMAND_NAME_FILTER, String.class)).thenReturn(serializedObject);
        when(serializer.deserialize(serializedObject)).thenReturn(COMMAND_NAME_FILTER);
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
        ConsistentHash testConsistentHash = new ConsistentHash().with(testMember, LOAD_FACTOR, commandMessage -> true);
        ReflectionUtils.setFieldValue(consistentHashField, testSubject, testConsistentHash);

        Optional<Member> resultOptional = testSubject.findDestination(TEST_COMMAND);

        assertTrue(resultOptional.isPresent());
        Member resultMember = resultOptional.orElseThrow(IllegalStateException::new);

        assertMember(SERVICE_INSTANCE_ID, SERVICE_INSTANCE_URI, resultMember);

        verify(routingStrategy).getRoutingKey(TEST_COMMAND);
    }

    @Test
    public void testUpdateMembershipUpdatesLocalServiceInstance() throws Exception {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        assertEquals(serviceInstanceMetadata.get("loadFactor"), Integer.toString(LOAD_FACTOR));
        assertEquals(serviceInstanceMetadata.get("serializedCommandFilter"), SERIALIZED_COMMAND_FILTER);
        assertEquals(serviceInstanceMetadata.get("serializedCommandFilterClassName"), SERIALIZED_COMMAND_FILTER_CLASS_NAME);

        verify(discoveryClient).getLocalServiceInstance();
        verify(serializer).serialize(COMMAND_NAME_FILTER, String.class);
        verify(serializer).deserialize(expectedSerializedObject);
    }

    @Test
    public void testUpdateMemberShipUpdatesConsistentHash() throws Exception {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        ConsistentHash resultConsistentHash = ReflectionUtils.getFieldValue(consistentHashField, testSubject);

        Set<Member> resultMemberSet = resultConsistentHash.getMembers();
        assertFalse(resultMemberSet.isEmpty());

        assertMember(SERVICE_INSTANCE_ID, SERVICE_INSTANCE_URI, resultMemberSet.iterator().next());

        verify(discoveryClient).getLocalServiceInstance();
        verify(serializer).serialize(COMMAND_NAME_FILTER, String.class);
        verify(serializer).deserialize(expectedSerializedObject);
    }

    @Test
    public void testUpdateMembershipsOnHeartbeatEventUpdatesConsistentHash() throws Exception {
        serviceInstanceMetadata.put("loadFactor", Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put("serializedCommandFilter", SERIALIZED_COMMAND_FILTER);
        serviceInstanceMetadata.put("serializedCommandFilterClassName", SERIALIZED_COMMAND_FILTER_CLASS_NAME);

        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        ConsistentHash resultConsistentHash = ReflectionUtils.getFieldValue(consistentHashField, testSubject);

        Set<Member> resultMemberSet = resultConsistentHash.getMembers();
        assertFalse(resultMemberSet.isEmpty());

        assertMember(SERVICE_INSTANCE_ID, SERVICE_INSTANCE_URI, resultMemberSet.iterator().next());

        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
        verify(serializer).deserialize(expectedSerializedObject);
    }

    @Test
    public void testUpdateMembershipsOnHeartbeatEventFiltersInstancesWithoutCommandRouterSpecificMetadata() throws Exception {
        int expectedMemberSetSize = 1;
        String expectedServiceInstanceId = "nonCommandRouterServiceInstance";

        serviceInstanceMetadata.put("loadFactor", Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put("serializedCommandFilter", SERIALIZED_COMMAND_FILTER);
        serviceInstanceMetadata.put("serializedCommandFilterClassName", SERIALIZED_COMMAND_FILTER_CLASS_NAME);

        ServiceInstance nonCommandRouterServiceInstance = mock(ServiceInstance.class);
        when(nonCommandRouterServiceInstance.getServiceId()).thenReturn(expectedServiceInstanceId);

        when(discoveryClient.getServices())
                .thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID, expectedServiceInstanceId));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID))
                .thenReturn(ImmutableList.of(serviceInstance, nonCommandRouterServiceInstance));

        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        ConsistentHash resultConsistentHash = ReflectionUtils.getFieldValue(consistentHashField, testSubject);

        Set<Member> resultMemberSet = resultConsistentHash.getMembers();
        assertEquals(expectedMemberSetSize, resultMemberSet.size());

        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
        verify(discoveryClient).getInstances(expectedServiceInstanceId);
        verify(serializer).deserialize(expectedSerializedObject);
    }

    private void assertMember(String expectedMemberName, URI expectedEndpoint, Member resultMember) {
        Assert.assertEquals(resultMember.getClass(), ConsistentHash.ConsistentHashMember.class);
        ConsistentHash.ConsistentHashMember result = (ConsistentHash.ConsistentHashMember) resultMember;
        Assert.assertEquals(result.name(), expectedMemberName);
        Assert.assertEquals(result.segmentCount(), LOAD_FACTOR);

        Optional<URI> connectionEndpointOptional = result.getConnectionEndpoint(URI.class);
        assertTrue(connectionEndpointOptional.isPresent());
        URI resultEndpoint = connectionEndpointOptional.orElseThrow(IllegalStateException::new);
        assertEquals(resultEndpoint, expectedEndpoint);
    }

}
