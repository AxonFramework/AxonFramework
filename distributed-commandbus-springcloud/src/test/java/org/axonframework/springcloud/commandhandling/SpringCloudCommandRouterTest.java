/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.springcloud.commandhandling;

import com.google.common.collect.ImmutableList;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.distributed.ConsistentHash;
import org.axonframework.commandhandling.distributed.ConsistentHashChangeListener;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.SimpleMember;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.junit.*;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.serviceregistry.Registration;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.axonframework.common.ReflectionUtils.getFieldValue;
import static org.axonframework.common.ReflectionUtils.setFieldValue;
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
    private static final Predicate<? super CommandMessage<?>> COMMAND_NAME_FILTER = c -> true;
    private static final boolean LOCAL_MEMBER = true;
    private static final boolean REMOTE_MEMBER = false;
    private static final boolean REGISTERED = true;
    private static final boolean NOT_REGISTERED = false;

    private SpringCloudCommandRouter testSubject;

    @Mock
    private DiscoveryClient discoveryClient;
    @Mock
    private Registration localServiceInstance;
    @Mock
    private RoutingStrategy routingStrategy;
    private Field atomicConsistentHashField;
    private Field blackListedServiceInstancesField;

    private String serializedCommandFilterData;
    private String serializedCommandFilterClassName;
    private HashMap<String, String> serviceInstanceMetadata;
    @Mock
    private ConsistentHashChangeListener consistentHashChangeListener;

    @Before
    public void setUp() throws Exception {
        serializedCommandFilterData = new XStreamSerializer().serialize(COMMAND_NAME_FILTER, String.class).getData();
        serializedCommandFilterClassName = COMMAND_NAME_FILTER.getClass().getName();

        String atomicConsistentHashFieldName = "atomicConsistentHash";
        atomicConsistentHashField = SpringCloudCommandRouter.class.getDeclaredField(atomicConsistentHashFieldName);

        String blackListedServiceInstancesFieldName = "blackListedServiceInstances";
        blackListedServiceInstancesField = SpringCloudCommandRouter.class.getDeclaredField(
                blackListedServiceInstancesFieldName);
        serviceInstanceMetadata = new HashMap<>();
        when(localServiceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(localServiceInstance.getUri()).thenReturn(SERVICE_INSTANCE_URI);
        when(localServiceInstance.getMetadata()).thenReturn(serviceInstanceMetadata);

        when(discoveryClient.getServices()).thenReturn(singletonList(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID))
                .thenReturn(singletonList(localServiceInstance));

        when(routingStrategy.getRoutingKey(any())).thenReturn(ROUTING_KEY);

        testSubject = new SpringCloudCommandRouter(discoveryClient,
                                                   localServiceInstance,
                                                   routingStrategy,
                                                   s -> true,
                                                   consistentHashChangeListener);
    }

    @Test
    public void testFindDestinationReturnsEmptyOptionalMemberForCommandMessage() {
        Optional<Member> result = testSubject.findDestination(TEST_COMMAND);

        assertFalse(result.isPresent());
        verify(routingStrategy).getRoutingKey(TEST_COMMAND);
    }

    @Test
    public void testFindDestinationReturnsMemberForCommandMessage() {
        SimpleMember<URI> testMember = new SimpleMember<>(
                SERVICE_INSTANCE_ID + "[" + SERVICE_INSTANCE_URI + "]", SERVICE_INSTANCE_URI, false, null
        );
        AtomicReference<ConsistentHash> testAtomicConsistentHash =
                new AtomicReference<>(new ConsistentHash().with(testMember, LOAD_FACTOR, commandMessage -> true));
        setFieldValue(atomicConsistentHashField, testSubject, testAtomicConsistentHash);

        Optional<Member> resultOptional = testSubject.findDestination(TEST_COMMAND);

        assertTrue(resultOptional.isPresent());
        Member resultMember = resultOptional.orElseThrow(IllegalStateException::new);

        assertMember(REMOTE_MEMBER, resultMember, NOT_REGISTERED);

        verify(routingStrategy).getRoutingKey(TEST_COMMAND);
    }

    @Test
    public void testUpdateMembershipUpdatesLocalServiceInstance() {
        Predicate<? super CommandMessage<?>> commandNameFilter = new CommandNameFilter(String.class.getName());
        String commandFilterData = new XStreamSerializer().serialize(commandNameFilter, String.class).getData();
        testSubject.updateMembership(LOAD_FACTOR, commandNameFilter);

        assertEquals(Integer.toString(LOAD_FACTOR), serviceInstanceMetadata.get(LOAD_FACTOR_KEY));
        assertEquals(commandFilterData, serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER_KEY));
        assertEquals(CommandNameFilter.class.getName(),
                     serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY));

        verify(consistentHashChangeListener).onConsistentHashChanged(argThat(item -> item.getMembers()
                                                                                         .stream()
                                                                                         .map(Member::name)
                                                                                         .anyMatch(memberName -> memberName
                                                                                                 .contains(
                                                                                                         SERVICE_INSTANCE_ID))));
    }

    @Test
    public void testUpdateMemberShipUpdatesConsistentHash() {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        AtomicReference<ConsistentHash> resultAtomicConsistentHash =
                getFieldValue(atomicConsistentHashField, testSubject);

        Set<Member> resultMemberSet = resultAtomicConsistentHash.get().getMembers();
        assertFalse(resultMemberSet.isEmpty());

        assertMember(LOCAL_MEMBER, resultMemberSet.iterator().next(), NOT_REGISTERED);
    }

    @Test
    public void testResetLocalMemberUpdatesConsistentHashByReplacingLocalMember() {
        serviceInstanceMetadata.put(LOAD_FACTOR_KEY, Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_KEY, serializedCommandFilterData);
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY, serializedCommandFilterClassName);

        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        AtomicReference<ConsistentHash> resultAtomicConsistentHash =
                getFieldValue(atomicConsistentHashField, testSubject);
        Set<Member> resultMemberSet = resultAtomicConsistentHash.get().getMembers();

        assertFalse(resultMemberSet.isEmpty());
        assertMember(LOCAL_MEMBER, resultMemberSet.iterator().next(), NOT_REGISTERED);

        testSubject.resetLocalMembership(mock(InstanceRegisteredEvent.class));

        resultAtomicConsistentHash = getFieldValue(atomicConsistentHashField, testSubject);
        resultMemberSet = resultAtomicConsistentHash.get().getMembers();

        assertFalse(resultMemberSet.isEmpty());
        assertMember(LOCAL_MEMBER, resultMemberSet.iterator().next(), REGISTERED);

        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
    }

    @Test
    public void testUpdateMembershipsOnHeartbeatEventUpdatesConsistentHash() {
        // Start up command router
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);
        // Set command router has passed the start up phase
        testSubject.resetLocalMembership(mock(InstanceRegisteredEvent.class));
        serviceInstanceMetadata.put(LOAD_FACTOR_KEY, Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_KEY, serializedCommandFilterData);
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY, serializedCommandFilterClassName);

        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        AtomicReference<ConsistentHash> resultAtomicConsistentHash =
                getFieldValue(atomicConsistentHashField, testSubject);

        Set<Member> resultMemberSet = resultAtomicConsistentHash.get().getMembers();
        assertFalse(resultMemberSet.isEmpty());

        assertMember(LOCAL_MEMBER, resultMemberSet.iterator().next(), REGISTERED);

        verify(discoveryClient, times(2)).getServices();
        verify(discoveryClient, times(2)).getInstances(SERVICE_INSTANCE_ID);
    }

    @Test
    public void testUpdateMembershipsAfterHeartbeatEventDoesNotOverwriteMembers() {
        serviceInstanceMetadata.put(LOAD_FACTOR_KEY, Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_KEY, serializedCommandFilterData);
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY, serializedCommandFilterClassName);

        String remoteServiceId = SERVICE_INSTANCE_ID + "-1";
        ServiceInstance remoteServiceInstance = mock(ServiceInstance.class);
        when(remoteServiceInstance.getMetadata()).thenReturn(serviceInstanceMetadata);
        when(remoteServiceInstance.getUri()).thenReturn(URI.create("remote"));
        when(remoteServiceInstance.getServiceId()).thenReturn(remoteServiceId);

        when(discoveryClient.getInstances(remoteServiceId)).thenReturn(ImmutableList.of(remoteServiceInstance));
        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID, remoteServiceId));

        testSubject.updateMemberships(mock(HeartbeatEvent.class));
        AtomicReference<ConsistentHash> resultAtomicConsistentHash =
                getFieldValue(atomicConsistentHashField, testSubject);

        Set<Member> resultMemberSet = resultAtomicConsistentHash.get().getMembers();
        assertEquals(2, resultMemberSet.size());

        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);
        AtomicReference<ConsistentHash> resultAtomicConsistentHashAfterLocalUpdate =
                getFieldValue(atomicConsistentHashField, testSubject);

        Set<Member> resultMemberSetAfterLocalUpdate = resultAtomicConsistentHashAfterLocalUpdate.get().getMembers();
        assertEquals(2, resultMemberSetAfterLocalUpdate.size());
    }

    @Test
    public void testUpdateMembershipsWithVanishedMemberOnHeartbeatEventRemovesMember() {
        // Start up command router
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);
        // Set router has passed the start up phase
        testSubject.resetLocalMembership(mock(InstanceRegisteredEvent.class));
        // Update router memberships with local and remote service instance
        serviceInstanceMetadata.put(LOAD_FACTOR_KEY, Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_KEY, serializedCommandFilterData);
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY, serializedCommandFilterClassName);

        String remoteServiceId = SERVICE_INSTANCE_ID + "-1";
        ServiceInstance remoteServiceInstance = mock(ServiceInstance.class);
        when(remoteServiceInstance.getMetadata()).thenReturn(serviceInstanceMetadata);
        when(remoteServiceInstance.getUri()).thenReturn(URI.create("remote"));
        when(remoteServiceInstance.getServiceId()).thenReturn(remoteServiceId);

        when(discoveryClient.getInstances(remoteServiceId)).thenReturn(ImmutableList.of(remoteServiceInstance));
        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID, remoteServiceId));

        testSubject.updateMemberships(mock(HeartbeatEvent.class));
        AtomicReference<ConsistentHash> resultAtomicConsistentHash =
                getFieldValue(atomicConsistentHashField, testSubject);

        Set<Member> resultMemberSet = resultAtomicConsistentHash.get().getMembers();
        assertEquals(2, resultMemberSet.size());

        // Evict remote service instance from discovery client and update router memberships
        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID));
        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        AtomicReference<ConsistentHash> resultAtomicConsistentHashAfterVanish =
                getFieldValue(atomicConsistentHashField, testSubject);

        Set<Member> resultMemberSetAfterVanish = resultAtomicConsistentHashAfterVanish.get().getMembers();
        assertEquals(1, resultMemberSetAfterVanish.size());
        assertMember(LOCAL_MEMBER, resultMemberSetAfterVanish.iterator().next(), REGISTERED);
    }

    @Test
    public void testUpdateMembershipsOnHeartbeatEventFiltersInstancesWithoutCommandRouterSpecificMetadata() {
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
                .thenReturn(ImmutableList.of(localServiceInstance, nonCommandRouterServiceInstance));

        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        AtomicReference<ConsistentHash> resultAtomicConsistentHash =
                getFieldValue(atomicConsistentHashField, testSubject);

        Set<Member> resultMemberSet = resultAtomicConsistentHash.get().getMembers();
        assertEquals(expectedMemberSetSize, resultMemberSet.size());

        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
        verify(discoveryClient).getInstances(expectedServiceInstanceId);
    }

    @Test
    public void testUpdateMembershipsOnHeartbeatEventBlackListsNonAxonInstances() throws Exception {
        SpringCloudCommandRouter testSubject = new SpringCloudCommandRouter(
                discoveryClient, localServiceInstance, routingStrategy, serviceInstance -> true
        );

        String blackListedInstancesFieldName = "blackListedServiceInstances";
        Field blackListedInstancesField =
                SpringCloudCommandRouter.class.getDeclaredField(blackListedInstancesFieldName);

        int expectedMemberSetSize = 1;
        String nonAxonServiceInstanceId = "nonAxonInstance";

        serviceInstanceMetadata.put(LOAD_FACTOR_KEY, Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_KEY, serializedCommandFilterData);
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY, serializedCommandFilterClassName);

        ServiceInstance nonAxonInstance = mock(ServiceInstance.class);
        when(nonAxonInstance.getServiceId()).thenReturn(nonAxonServiceInstanceId);

        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID, nonAxonServiceInstanceId));
        when(discoveryClient.getInstances(nonAxonServiceInstanceId)).thenReturn(ImmutableList.of(nonAxonInstance));

        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        AtomicReference<ConsistentHash> resultAtomicConsistentHash =
                getFieldValue(atomicConsistentHashField, testSubject);
        Set<Member> resultMemberSet = resultAtomicConsistentHash.get().getMembers();
        assertEquals(expectedMemberSetSize, resultMemberSet.size());

        Set<ServiceInstance> resultBlackList = getFieldValue(blackListedInstancesField, testSubject);
        assertTrue(resultBlackList.contains(nonAxonInstance));

        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
        verify(discoveryClient).getInstances(nonAxonServiceInstanceId);
    }

    @Test
    public void testUpdateMembershipsOnHeartbeatEventDoesNotRequestInfoFromBlackListedServiceInstance() {
        SpringCloudCommandRouter testSubject = new SpringCloudCommandRouter(
                discoveryClient, localServiceInstance, routingStrategy, serviceInstance -> true
        );

        serviceInstanceMetadata.put(LOAD_FACTOR_KEY, Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_KEY, serializedCommandFilterData);
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY, serializedCommandFilterClassName);

        String nonAxonServiceInstanceId = "nonAxonInstance";
        ServiceInstance nonAxonInstance = mock(ServiceInstance.class);
        when(nonAxonInstance.getServiceId()).thenReturn(nonAxonServiceInstanceId);
        when(nonAxonInstance.getHost()).thenReturn("nonAxonHost");
        when(nonAxonInstance.getPort()).thenReturn(0);
        when(nonAxonInstance.getMetadata()).thenReturn(Collections.emptyMap());

        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID, nonAxonServiceInstanceId));
        when(discoveryClient.getInstances(nonAxonServiceInstanceId)).thenReturn(ImmutableList.of(nonAxonInstance));

        // First update - black lists 'nonAxonServiceInstance' as it does not contain any message routing information
        testSubject.updateMemberships(mock(HeartbeatEvent.class));
        // Second update
        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        verify(discoveryClient, times(2)).getServices();
        verify(discoveryClient, times(2)).getInstances(nonAxonServiceInstanceId);
        verify(discoveryClient, times(2)).getInstances(SERVICE_INSTANCE_ID);
    }

    @Test
    public void testUpdateMembershipsOnHeartbeatEventTwoInstancesOnSameServiceIdUpdatesConsistentHash() {
        int expectedMemberSetSize = 2;

        serviceInstanceMetadata.put(LOAD_FACTOR_KEY, Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_KEY, serializedCommandFilterData);
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY, serializedCommandFilterClassName);

        ServiceInstance remoteInstance = mock(ServiceInstance.class);
        when(remoteInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(remoteInstance.getUri()).thenReturn(URI.create("remote"));
        when(remoteInstance.getMetadata()).thenReturn(serviceInstanceMetadata);

        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID))
                .thenReturn(ImmutableList.of(localServiceInstance, remoteInstance));

        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        AtomicReference<ConsistentHash> resultAtomicConsistentHash =
                getFieldValue(atomicConsistentHashField, testSubject);

        Set<Member> resultMemberSet = resultAtomicConsistentHash.get().getMembers();
        assertEquals(expectedMemberSetSize, resultMemberSet.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBlackListCleaning() {
        serviceInstanceMetadata.put(LOAD_FACTOR_KEY, Integer.toString(LOAD_FACTOR));
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_KEY, serializedCommandFilterData);
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY, serializedCommandFilterClassName);

        String serviceInstanceToBeBlackListedId = "toBeBlackListed";
        ServiceInstance serviceInstanceToBeBlackListed = mock(ServiceInstance.class);
        when(serviceInstanceToBeBlackListed.getServiceId()).thenReturn(serviceInstanceToBeBlackListedId);
        when(serviceInstanceToBeBlackListed.getHost()).thenReturn("host");
        when(serviceInstanceToBeBlackListed.getPort()).thenReturn(0);
        when(serviceInstanceToBeBlackListed.getMetadata()).thenReturn(
                Collections.emptyMap(),
                serviceInstanceMetadata
        );

        when(discoveryClient.getServices()).thenReturn(
                ImmutableList.of(SERVICE_INSTANCE_ID, serviceInstanceToBeBlackListedId),
                ImmutableList.of(SERVICE_INSTANCE_ID),
                ImmutableList.of(SERVICE_INSTANCE_ID, serviceInstanceToBeBlackListedId)
        );
        when(discoveryClient.getInstances(serviceInstanceToBeBlackListedId)).thenReturn(ImmutableList
                                                                                                .of(serviceInstanceToBeBlackListed));

        testSubject.updateMemberships(mock(HeartbeatEvent.class));
        Set<ServiceInstance> blackListed = getFieldValue(blackListedServiceInstancesField, testSubject);
        assertEquals(1, blackListed.size());
        assertEquals(serviceInstanceToBeBlackListedId, blackListed.iterator().next().getServiceId());

        testSubject.updateMemberships(mock(HeartbeatEvent.class));
        blackListed = getFieldValue(blackListedServiceInstancesField, testSubject);
        assertTrue(blackListed.isEmpty());

        testSubject.updateMemberships(mock(HeartbeatEvent.class));
        blackListed = getFieldValue(blackListedServiceInstancesField, testSubject);
        assertTrue(blackListed.isEmpty());
    }

    private void assertMember(boolean localMember, Member resultMember, Boolean registered) {
        String expectedMemberName = SpringCloudCommandRouterTest.SERVICE_INSTANCE_ID;
        URI expectedEndpoint = SpringCloudCommandRouterTest.SERVICE_INSTANCE_URI;

        assertEquals(resultMember.getClass(), ConsistentHash.ConsistentHashMember.class);
        ConsistentHash.ConsistentHashMember result = (ConsistentHash.ConsistentHashMember) resultMember;
        if (localMember && !registered) {
            assertTrue(result.name().contains(expectedMemberName));
        } else {
            assertEquals(expectedMemberName + "[" + expectedEndpoint + "]", result.name());
        }
        assertEquals(LOAD_FACTOR, result.segmentCount());

        Optional<URI> connectionEndpointOptional = result.getConnectionEndpoint(URI.class);
        if (localMember && !registered) {
            assertFalse(connectionEndpointOptional.isPresent());
        } else {
            assertTrue(connectionEndpointOptional.isPresent());
            URI resultEndpoint = connectionEndpointOptional.orElseThrow(IllegalStateException::new);
            assertEquals(resultEndpoint, expectedEndpoint);
        }
    }

    @Test
    public void testConsistentHashCreatedOnDistinctHostsShouldBeEqual() {
        serviceInstanceMetadata.put(LOAD_FACTOR_KEY, Integer.toString(100));
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_KEY, serializedCommandFilterData);
        serviceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME_KEY, serializedCommandFilterClassName);

        when(discoveryClient.getServices()).thenReturn(singletonList(SERVICE_INSTANCE_ID));

        int numberOfInstances = 6;
        List<ServiceInstance> serviceInstances = mockServiceInstances(numberOfInstances);
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID)).thenReturn(serviceInstances);

        List<SpringCloudCommandRouter> routers = createRoutersFor(serviceInstances);
        initAll(routers);

        List<ConsistentHash> hashes = getHashesFor(routers);

        assertEquals(hashes.size(), numberOfInstances);
        hashes.forEach(hash -> assertEquals(hash, hashes.get(0)));
    }

    private List<ConsistentHash> getHashesFor(List<SpringCloudCommandRouter> routers) {
        return routers.stream()
                      .map(this::getHash)
                      .map(AtomicReference::get)
                      .collect(toList());
    }

    private void initAll(List<SpringCloudCommandRouter> routers) {
        routers.forEach(r -> {
            r.updateMemberships(mock(HeartbeatEvent.class));
            r.resetLocalMembership(mock(InstanceRegisteredEvent.class));
        });
    }

    private List<SpringCloudCommandRouter> createRoutersFor(List<ServiceInstance> serviceInstances) {
        return serviceInstances.stream()
                               .map(ServiceInstance::getHost)
                               .map(this::createRouterFor)
                               .collect(toList());
    }

    private AtomicReference<ConsistentHash> getHash(SpringCloudCommandRouter r) {
        return getFieldValue(atomicConsistentHashField, r);
    }

    private SpringCloudCommandRouter createRouterFor(String host) {
        Registration localServiceInstance = mock(Registration.class);
        when(localServiceInstance.getUri()).thenReturn(URI.create("http://" + host));
        return new SpringCloudCommandRouter(discoveryClient,
                                            localServiceInstance,
                                            routingStrategy,
                                            s -> true,
                                            consistentHashChangeListener);
    }

    private List<ServiceInstance> mockServiceInstances(int number) {
        return IntStream.rangeClosed(1, number)
                        .mapToObj(i -> {
                            ServiceInstance instance = mock(ServiceInstance.class);
                            when(instance.getHost()).thenReturn("host" + i);
                            when(instance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
                            when(instance.getUri()).thenReturn(URI.create("http://host" + i));
                            when(instance.getMetadata()).thenReturn(serviceInstanceMetadata);
                            return instance;
                        })
                        .collect(toList());
    }
}
