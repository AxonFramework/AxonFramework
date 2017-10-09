/*
 * Copyright (c) 2010-2017. Axon Framework
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

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandRouter;
import org.axonframework.commandhandling.distributed.ConsistentHash;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.SimpleMember;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.event.EventListener;

/**
 * A {@link org.axonframework.commandhandling.distributed.CommandRouter} implementation which uses Spring Clouds {@link
 * org.springframework.cloud.client.discovery.DiscoveryClient}s to discover and notify other nodes for routing
 * Commands.
 *
 * @author Steven van Beelen
 */
public class SpringCloudCommandRouter implements CommandRouter {

    private static final String LOAD_FACTOR = "loadFactor";
    private static final String SERIALIZED_COMMAND_FILTER = "serializedCommandFilter";
    private static final String SERIALIZED_COMMAND_FILTER_CLASS_NAME = "serializedCommandFilterClassName";

    private static final boolean OVERWRITE_MEMBERS = true;
    private static final boolean DO_NOT_OVERWRITE_MEMBERS = false;

    private final DiscoveryClient discoveryClient;
    private final RoutingStrategy routingStrategy;
    protected final XStreamSerializer serializer = new XStreamSerializer();
    private final Predicate<ServiceInstance> serviceInstanceFilter;
    private final AtomicReference<ConsistentHash> atomicConsistentHash = new AtomicReference<>(new ConsistentHash());

    /**
     * Initialize a {@link org.axonframework.commandhandling.distributed.CommandRouter} with the given {@link
     * org.springframework.cloud.client.discovery.DiscoveryClient} to update it's own membership as a {@code
     * CommandRouter} and to create it's own awareness of available nodes to send commands to in a {@link
     * org.axonframework.commandhandling.distributed.ConsistentHash}.
     * The {@code routingStrategy} is used to define the key based on which Command Messages are routed to their
     * respective handler nodes.
     * The {@code serializer} is used to serialize this node it's set of Commands it can handle to be added as meta
     * data to this {@link org.springframework.cloud.client.ServiceInstance}
     *
     * @param discoveryClient The {@code DiscoveryClient} used to discovery and notify other nodes
     * @param routingStrategy The strategy for routing Commands to a Node
     * @param serializer      The serializer used to serialize this node it's set of Commands it can handle
     * @deprecated {@code serializer} is no longer customizable
     */
    @Deprecated
    public SpringCloudCommandRouter(DiscoveryClient discoveryClient, RoutingStrategy routingStrategy,
                                    Serializer serializer) {
        this(discoveryClient, routingStrategy);
    }

    /**
     * Initialize a {@link org.axonframework.commandhandling.distributed.CommandRouter} with the given
     * {@link org.springframework.cloud.client.discovery.DiscoveryClient} to update it's own membership as a
     * {@code CommandRouter} and to create it's own awareness of available nodes to send commands to in a
     * {@link org.axonframework.commandhandling.distributed.ConsistentHash}.
     * The {@code routingStrategy} is used to define the key based on which Command Messages are routed to their
     * respective handler nodes.
     * Uses a default {@code Predicate<ServiceInstance>} filter function to remove any
     * {@link org.springframework.cloud.client.ServiceInstance}' which miss membership information key value pairs in
     * their metadata. This is thus the
     * {@link #serviceInstanceMetadataContainsMembershipInformation(ServiceInstance serviceInstance)} function.
     *
     * @param discoveryClient The {@code DiscoveryClient} used to discovery and notify other nodes
     * @param routingStrategy The strategy for routing Commands to a Node
     */
    public SpringCloudCommandRouter(DiscoveryClient discoveryClient, RoutingStrategy routingStrategy) {
        this(
                discoveryClient,
                routingStrategy,
                SpringCloudCommandRouter::serviceInstanceMetadataContainsMembershipInformation
        );
    }

    /**
     * Initialize a {@link org.axonframework.commandhandling.distributed.CommandRouter} with the given {@link
     * org.springframework.cloud.client.discovery.DiscoveryClient} to update it's own membership as a {@code
     * CommandRouter} and to create it's own awareness of available nodes to send commands to in a {@link
     * org.axonframework.commandhandling.distributed.ConsistentHash}. The {@code routingStrategy} is used to define the
     * key based on which Command Messages are routed to their respective handler nodes.
     * A {@code Predicate<ServiceInstance>} to filter a {@link org.springframework.cloud.client.ServiceInstance}
     * from the membership update loop.
     *
     * @param discoveryClient The {@code DiscoveryClient} used to discovery and notify other nodes
     * @param routingStrategy The strategy for routing Commands to a Node
     * @param serviceInstanceFilter The {@code Predicate<ServiceInstance>} used to filter
     * {@link org.springframework.cloud.client.ServiceInstance}' from the update membership loop.
     */
    public SpringCloudCommandRouter(DiscoveryClient discoveryClient,
                                    RoutingStrategy routingStrategy,
                                    Predicate<ServiceInstance> serviceInstanceFilter) {
        this.discoveryClient = discoveryClient;
        this.routingStrategy = routingStrategy;
        this.serviceInstanceFilter = serviceInstanceFilter;
    }

    @Override
    public Optional<Member> findDestination(CommandMessage<?> commandMessage) {
        return atomicConsistentHash.get().getMember(routingStrategy.getRoutingKey(commandMessage), commandMessage);
    }

    @Override
    public void updateMembership(int loadFactor, Predicate<? super CommandMessage<?>> commandFilter) {
        ServiceInstance localServiceInstance = this.discoveryClient.getLocalServiceInstance();
        Map<String, String> localServiceInstanceMetadata = localServiceInstance.getMetadata();
        localServiceInstanceMetadata.put(LOAD_FACTOR, Integer.toString(loadFactor));
        SerializedObject<String> serializedCommandFilter = serializer.serialize(commandFilter, String.class);
        localServiceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER, serializedCommandFilter.getData());
        localServiceInstanceMetadata.put(
                SERIALIZED_COMMAND_FILTER_CLASS_NAME, serializedCommandFilter.getType().getName()
        );

        updateMemberships(Collections.singleton(localServiceInstance), DO_NOT_OVERWRITE_MEMBERS);
    }

    @EventListener
    @SuppressWarnings("UnusedParameters")
    public void updateMemberships(HeartbeatEvent event) {
        Set<ServiceInstance> allServiceInstances =
                discoveryClient.getServices().stream()
                               .map(discoveryClient::getInstances)
                               .flatMap(Collection::stream)
                               .filter(serviceInstanceFilter)
                               .collect(Collectors.toSet());
        updateMemberships(allServiceInstances, OVERWRITE_MEMBERS);
    }

    /**
     * Update the router memberships.
     *
     * @param serviceInstances Services instances to add
     * @param overwrite        True to evict members absent from serviceInstances
     */
    private void updateMemberships(Set<ServiceInstance> serviceInstances, boolean overwrite) {
        AtomicReference<ConsistentHash> updatedConsistentHash = overwrite ?
                new AtomicReference<>(new ConsistentHash()) : atomicConsistentHash;

        serviceInstances.forEach(
                serviceInstance -> updateMembershipForServiceInstance(serviceInstance, updatedConsistentHash)
        );

        atomicConsistentHash.set(updatedConsistentHash.get());
    }

    private void updateMembershipForServiceInstance(ServiceInstance serviceInstance,
                                                    AtomicReference<ConsistentHash> atomicConsistentHash) {
        SimpleMember<URI> simpleMember = buildSimpleMember(serviceInstance);
        MembershipInformation membershipInformation = getMembershipInformationFrom(serviceInstance);

        atomicConsistentHash.updateAndGet(
                consistentHash -> consistentHash.with(simpleMember,
                                                      membershipInformation.getLoadFactor(),
                                                      membershipInformation.getCommandFilter(serializer))
        );
    }

    SimpleMember<URI> buildSimpleMember(ServiceInstance serviceInstance) {
        URI serviceUri = serviceInstance.getUri();
        String serviceId = serviceInstance.getServiceId();

        ServiceInstance localServiceInstance = discoveryClient.getLocalServiceInstance();
        URI localServiceUri = localServiceInstance.getUri();
        boolean local = localServiceUri.equals(serviceUri);

        return new SimpleMember<>(
                serviceId.toUpperCase() + "[" + serviceInstance.getUri() + "]",
                serviceInstance.getUri(),
                local,
                member -> atomicConsistentHash.updateAndGet(consistentHash -> consistentHash.without(member))
        );
    }

    private MembershipInformation getMembershipInformationFrom(ServiceInstance serviceInstance) {
        return serviceInstanceMetadataContainsMembershipInformation(serviceInstance) ?
                membershipInformationFromMetadata(serviceInstance.getMetadata()) :
                membershipInformationFromNonMetadataSource(serviceInstance);
    }

    /**
     * Boolean check if the metadata {@link java.util.Map} of the given
     * {@link org.springframework.cloud.client.ServiceInstance} contains any of the expected membership info keys.
     *
     * @param serviceInstance The {@link org.springframework.cloud.client.ServiceInstance} to check its metadata keys
     *                        from
     * @return true if the given {@code serviceInstance} contains all expected membership info keys;
     * false if one of the expected membership info keys is missing.
     */
    public static boolean serviceInstanceMetadataContainsMembershipInformation(ServiceInstance serviceInstance) {
        Map<String, String> serviceInstanceMetadata = serviceInstance.getMetadata();
        return serviceInstanceMetadata.containsKey(LOAD_FACTOR) &&
                serviceInstanceMetadata.containsKey(SERIALIZED_COMMAND_FILTER) &&
                serviceInstanceMetadata.containsKey(SERIALIZED_COMMAND_FILTER_CLASS_NAME);
    }

    private MembershipInformation membershipInformationFromMetadata(Map<String, String> serviceInstanceMetadata) {
        int loadFactor = Integer.parseInt(serviceInstanceMetadata.get(LOAD_FACTOR));
        SimpleSerializedObject<String> serializedObject = new SimpleSerializedObject<>(
                serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER), String.class,
                serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER_CLASS_NAME), null);

        return new MembershipInformation(loadFactor, serializedObject);
    }

    protected MembershipInformation membershipInformationFromNonMetadataSource(ServiceInstance serviceInstance) {
        throw new UnsupportedOperationException(
                "The default " + this.getClass().getSimpleName() + " does not support membership information " +
                        "retrieval from another source than a ServiceInstance its metadata."
        );
    }

}
