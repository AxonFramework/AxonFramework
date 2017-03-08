package org.axonframework.springcloud.commandhandling;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.*;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A {@link org.axonframework.commandhandling.distributed.CommandRouter} implementation which uses Spring Clouds
 * {@link org.springframework.cloud.client.discovery.DiscoveryClient}s to discover and notify other nodes for routing
 * Commands.
 */
public class SpringCloudCommandRouter implements CommandRouter {

    private static final String LOAD_FACTOR = "loadFactor";
    private static final String SERIALIZED_COMMAND_FILTER = "serializedCommandFilter";
    private static final String SERIALIZED_COMMAND_FILTER_CLASS_NAME = "serializedCommandFilterClassName";

    private final DiscoveryClient discoveryClient;
    private final RoutingStrategy routingStrategy;
    private final XStreamSerializer serializer = new XStreamSerializer();
    private final AtomicReference<ConsistentHash> atomicConsistentHash = new AtomicReference<>(new ConsistentHash());

    /**
     * Initialize a {@link org.axonframework.commandhandling.distributed.CommandRouter} with the given
     * {@link org.springframework.cloud.client.discovery.DiscoveryClient} to update it's own membership as a
     * {@code CommandRouter} and to create it's own awareness of available nodes to send commands to in a
     * {@link org.axonframework.commandhandling.distributed.ConsistentHash}. The {@code routingStrategy} is used to
     * define the key based on which Command Messages are routed to their respective handler nodes.
     * The {@code serializer} is used to serialize this node it's set of Commands it can handle to be added as meta data
     * to this {@link org.springframework.cloud.client.ServiceInstance}
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
     * {@link org.axonframework.commandhandling.distributed.ConsistentHash}. The {@code routingStrategy} is used to
     * define the key based on which Command Messages are routed to their respective handler nodes.
     *
     * @param discoveryClient The {@code DiscoveryClient} used to discovery and notify other nodes
     * @param routingStrategy The strategy for routing Commands to a Node
     */
    public SpringCloudCommandRouter(DiscoveryClient discoveryClient, RoutingStrategy routingStrategy) {
        this.discoveryClient = discoveryClient;
        this.routingStrategy = routingStrategy;
    }

    @Override
    public Optional<Member> findDestination(CommandMessage<?> commandMessage) {
        return atomicConsistentHash.get().getMember(routingStrategy.getRoutingKey(commandMessage), commandMessage);
    }

    @Override
    public void updateMembership(int loadFactor, Predicate<CommandMessage<?>> commandFilter) {
        ServiceInstance localServiceInstance = this.discoveryClient.getLocalServiceInstance();
        Map<String, String> localServiceInstanceMetadata = localServiceInstance.getMetadata();
        localServiceInstanceMetadata.put(LOAD_FACTOR, Integer.toString(loadFactor));
        SerializedObject<String> serializedCommandFilter = serializer.serialize(commandFilter, String.class);
        localServiceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER, serializedCommandFilter.getData());
        localServiceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME, serializedCommandFilter.getType().getName());

        updateMemberships(Collections.singleton(localServiceInstance));
    }

    @EventListener
    @SuppressWarnings("UnusedParameters")
    public void updateMemberships(HeartbeatEvent event) {
        Set<ServiceInstance> allServiceInstances = discoveryClient.getServices().stream()
                .map(discoveryClient::getInstances)
                .flatMap(Collection::stream)
                .filter(serviceInstance -> serviceInstance.getMetadata().containsKey(LOAD_FACTOR) &&
                        serviceInstance.getMetadata().containsKey(SERIALIZED_COMMAND_FILTER) &&
                        serviceInstance.getMetadata().containsKey(SERIALIZED_COMMAND_FILTER_CLASS_NAME))
                .collect(Collectors.toSet());
        updateMemberships(allServiceInstances);
    }

    private void updateMemberships(Set<ServiceInstance> serviceInstances) {
        serviceInstances.forEach(serviceInstance -> {
            SimpleMember<URI> simpleMember = new SimpleMember<>(
                    serviceInstance.getServiceId().toUpperCase() + "[" + serviceInstance.getUri() + "]",
                    serviceInstance.getUri(),
                    member -> atomicConsistentHash.updateAndGet(consistentHash -> consistentHash.without(member))
            );

            Map<String, String> serviceInstanceMetadata = serviceInstance.getMetadata();

            int loadFactor = Integer.parseInt(serviceInstanceMetadata.get(LOAD_FACTOR));
            SimpleSerializedObject<String> serializedObject = new SimpleSerializedObject<>(
                    serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER), String.class,
                    serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER_CLASS_NAME), null);
            CommandNameFilter commandNameFilter = serializer.deserialize(serializedObject);

            atomicConsistentHash.updateAndGet(
                    consistentHash -> consistentHash.with(simpleMember, loadFactor, commandNameFilter)
            );
        });
    }

}
