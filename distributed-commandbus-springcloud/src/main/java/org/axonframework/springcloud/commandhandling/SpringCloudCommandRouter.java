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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * A {@link org.axonframework.commandhandling.distributed.CommandRouter} implementation which uses Spring Cloud's
 * {@link org.springframework.cloud.client.discovery.DiscoveryClient}s to propagate its CommandMessage Routing
 * Information, and to discover other Axon nodes and retrieve their Message Routing Information.
 * It does so by utilizing the metadata contained in a {@link org.springframework.cloud.client.ServiceInstance} for
 * storing the Message Routing Information in.
 * Other nodes discovered through the DiscoveryClient system which do not contain any of the required Message Routing
 * Information fields will be black listed, so not to perform any unneeded additional checks on that node.
 *
 * @author Steven van Beelen
 */
public class SpringCloudCommandRouter implements CommandRouter {

    private static final Logger logger = LoggerFactory.getLogger(SpringCloudCommandRouter.class);

    private static final String LOAD_FACTOR = "loadFactor";
    private static final String SERIALIZED_COMMAND_FILTER = "serializedCommandFilter";
    private static final String SERIALIZED_COMMAND_FILTER_CLASS_NAME = "serializedCommandFilterClassName";

    private final DiscoveryClient discoveryClient;
    private final RoutingStrategy routingStrategy;
    protected final XStreamSerializer serializer = new XStreamSerializer();
    private final Predicate<ServiceInstance> serviceInstanceFilter;
    private final AtomicReference<ConsistentHash> atomicConsistentHash = new AtomicReference<>(new ConsistentHash());
    private final Set<ServiceInstance> blackListedServiceInstances = new HashSet<>();

    /**
     * Initialize a {@link org.axonframework.commandhandling.distributed.CommandRouter} with the given {@link
     * org.springframework.cloud.client.discovery.DiscoveryClient} to update its own membership as a {@code
     * CommandRouter} and to create its own awareness of available nodes to send commands to in a {@link
     * org.axonframework.commandhandling.distributed.ConsistentHash}.
     * The {@code routingStrategy} is used to define the key based on which Command Messages are routed to their
     * respective handler nodes.
     * The {@code serializer} is used to serialize this node its set of Commands it can handle to be added as meta data
     * to this {@link org.springframework.cloud.client.ServiceInstance}.
     * Uses a default {@code Predicate<ServiceInstance>} filter function to remove any
     * {@link org.springframework.cloud.client.ServiceInstance} which miss message routing information key-value pairs
     * in their metadata. This is thus the
     * {@link #serviceInstanceMetadataContainsMessageRoutingInformation(ServiceInstance serviceInstance)} function.
     *
     * @param discoveryClient The {@code DiscoveryClient} used to discovery and notify other nodes
     * @param routingStrategy The strategy for routing Commands to a Node
     * @param serializer      The serializer used to serialize this node its set of Commands it can handle
     * @deprecated {@code serializer} is no longer customizable
     */
    @Deprecated
    public SpringCloudCommandRouter(DiscoveryClient discoveryClient, RoutingStrategy routingStrategy,
                                    Serializer serializer) {
        this(discoveryClient, routingStrategy);
    }

    /**
     * Initialize a {@link org.axonframework.commandhandling.distributed.CommandRouter} with the given {@link
     * org.springframework.cloud.client.discovery.DiscoveryClient} to update its own membership as a {@code
     * CommandRouter} and to create its own awareness of available nodes to send commands to in a {@link
     * org.axonframework.commandhandling.distributed.ConsistentHash}.
     * The {@code routingStrategy} is used to define the key based on which Command Messages are routed to their
     * respective handler nodes.
     * Uses a default {@code Predicate<ServiceInstance>} filter function to remove any
     * {@link org.springframework.cloud.client.ServiceInstance} which miss message routing information key-value pairs
     * in their metadata. This is thus the
     * {@link #serviceInstanceMetadataContainsMessageRoutingInformation(ServiceInstance serviceInstance)} function.
     *
     * @param discoveryClient The {@code DiscoveryClient} used to discovery and notify other nodes
     * @param routingStrategy The strategy for routing Commands to a Node
     */
    public SpringCloudCommandRouter(DiscoveryClient discoveryClient, RoutingStrategy routingStrategy) {
        this(discoveryClient,
             routingStrategy,
             SpringCloudCommandRouter::serviceInstanceMetadataContainsMessageRoutingInformation);
    }

    /**
     * Initialize a {@link org.axonframework.commandhandling.distributed.CommandRouter} with the given {@link
     * org.springframework.cloud.client.discovery.DiscoveryClient} to update its own membership as a {@code
     * CommandRouter} and to create its own awareness of available nodes to send commands to in a {@link
     * org.axonframework.commandhandling.distributed.ConsistentHash}.
     * The {@code routingStrategy} is used to define the key based on which Command Messages are routed to their
     * respective handler nodes.
     * A {@code Predicate<ServiceInstance>} to filter a {@link org.springframework.cloud.client.ServiceInstance} from
     * the membership update loop.
     *
     * @param discoveryClient       The {@code DiscoveryClient} used to discovery and notify other nodes
     * @param routingStrategy       The strategy for routing Commands to a Node
     * @param serviceInstanceFilter The {@code Predicate<ServiceInstance>} used to filter {@link
     *                              org.springframework.cloud.client.ServiceInstance} from the update membership loop.
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

        updateMembershipForServiceInstance(localServiceInstance, atomicConsistentHash);
    }

    @EventListener
    @SuppressWarnings("UnusedParameters")
    public void updateMemberships(HeartbeatEvent event) {
        AtomicReference<ConsistentHash> updatedConsistentHash = new AtomicReference<>(new ConsistentHash());

        discoveryClient.getServices().stream()
                       .map(discoveryClient::getInstances)
                       .flatMap(Collection::stream)
                       .filter(serviceInstanceFilter)
                       .filter(this::ifNotBlackListed)
                       .forEach(serviceInstance -> updateMembershipForServiceInstance(serviceInstance,
                                                                                      updatedConsistentHash));

        atomicConsistentHash.set(updatedConsistentHash.get());
    }

    private boolean ifNotBlackListed(ServiceInstance serviceInstance) {
        return !blackListedServiceInstances.contains(serviceInstance);
    }

    private void updateMembershipForServiceInstance(ServiceInstance serviceInstance,
                                                    AtomicReference<ConsistentHash> atomicConsistentHash) {
        SimpleMember<URI> simpleMember = buildSimpleMember(serviceInstance);

        Optional<MessageRoutingInformation> optionalMessageRoutingInfo = getMessageRoutingInformation(serviceInstance);

        if (optionalMessageRoutingInfo.isPresent()) {
            MessageRoutingInformation messageRoutingInfo = optionalMessageRoutingInfo.get();
            atomicConsistentHash.updateAndGet(
                    consistentHash -> consistentHash.with(simpleMember,
                                                          messageRoutingInfo.getLoadFactor(),
                                                          messageRoutingInfo.getCommandFilter(serializer))
            );
        } else {
            logger.info(
                    "Black listed ServiceInstance [{}] under host [{}] and port [{}] since we could not retrieve the "
                            + "required Message Routing Information from it.",
                    serviceInstance.getServiceId(), serviceInstance.getHost(), serviceInstance.getPort()
            );
            blackListedServiceInstances.add(serviceInstance);
        }
    }

    /**
     * Instantiate a {@link SimpleMember} of type {@link java.net.URI} based on the provided {@code serviceInstance}.
     * This SimpleMember is later used to send, for example, Command messages to.
     *
     * @param serviceInstance A {@link org.springframework.cloud.client.ServiceInstance} to build a {@link SimpleMember}
     *                        for.
     * @return A {@link SimpleMember} based on the contents of the provided {@code serviceInstance}.
     */
    protected SimpleMember<URI> buildSimpleMember(ServiceInstance serviceInstance) {
        URI serviceUri = serviceInstance.getUri();
        String serviceId = serviceInstance.getServiceId();

        ServiceInstance localServiceInstance = discoveryClient.getLocalServiceInstance();
        URI localServiceUri = localServiceInstance.getUri();
        boolean local = localServiceUri.equals(serviceUri);

        return new SimpleMember<>(
                serviceId.toUpperCase() + "[" + serviceUri + "]",
                serviceUri,
                local,
                member -> atomicConsistentHash.updateAndGet(consistentHash -> consistentHash.without(member))
        );
    }

    /**
     * Retrieve the {@link MessageRoutingInformation} of the provided {@code serviceInstance}. If the
     * MessageRoutingInformation could not be found, an {@code Optional.empty()} will be returned. Otherwise the
     * MessageRoutingInformation will be contained in the Optional.
     *
     * @param serviceInstance A {@link org.springframework.cloud.client.ServiceInstance} to retrieve {@link
     *                        MessageRoutingInformation} from.
     * @return an {@link Optional} of type {@link MessageRoutingInformation}, containing the corresponding
     * MessageRoutingInformation for the given {@code serviceInstance} if it could be retrieved.
     */
    protected Optional<MessageRoutingInformation> getMessageRoutingInformation(ServiceInstance serviceInstance) {
        if (!serviceInstanceMetadataContainsMessageRoutingInformation(serviceInstance)) {
            return Optional.empty();
        }

        Map<String, String> serviceInstanceMetadata = serviceInstance.getMetadata();

        int loadFactor = Integer.parseInt(serviceInstanceMetadata.get(LOAD_FACTOR));
        SimpleSerializedObject<String> serializedCommandFilter = new SimpleSerializedObject<>(
                serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER), String.class,
                serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER_CLASS_NAME), null
        );
        return Optional.of(new MessageRoutingInformation(loadFactor, serializedCommandFilter));
    }

    /**
     * Boolean check if the metadata {@link java.util.Map} of the given
     * {@link org.springframework.cloud.client.ServiceInstance} contains any of the expected message routing info keys.
     *
     * @param serviceInstance The {@link org.springframework.cloud.client.ServiceInstance} to check its metadata keys
     *                        from
     * @return true if the given {@code serviceInstance} contains all expected message routing info keys; false if one
     * of the expected message routing info keys is missing.
     */
    public static boolean serviceInstanceMetadataContainsMessageRoutingInformation(ServiceInstance serviceInstance) {
        Map<String, String> serviceInstanceMetadata = serviceInstance.getMetadata();
        return serviceInstanceMetadata.containsKey(LOAD_FACTOR) &&
                serviceInstanceMetadata.containsKey(SERIALIZED_COMMAND_FILTER) &&
                serviceInstanceMetadata.containsKey(SERIALIZED_COMMAND_FILTER_CLASS_NAME);
    }
}
