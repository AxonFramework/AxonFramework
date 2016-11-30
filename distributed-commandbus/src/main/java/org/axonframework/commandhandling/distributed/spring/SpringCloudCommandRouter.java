package org.axonframework.commandhandling.distributed.spring;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.*;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SpringCloudCommandRouter implements CommandRouter {

    private static final String LOAD_FACTOR = "loadFactor";
    private static final String SERIALIZED_COMMAND_FILTER = "serializedCommandFilter";
    private static final String SERIALIZED_COMMAND_FILTER_CLASS_NAME = "serializedCommandFilterClassName";

    private final DiscoveryClient discoveryClient;
    private final RoutingStrategy routingStrategy;
    private final Serializer serializer;
    private ConsistentHash consistentHash = new ConsistentHash();

    public SpringCloudCommandRouter(DiscoveryClient discoveryClient, RoutingStrategy routingStrategy,
                                    Serializer serializer) {
        this.discoveryClient = discoveryClient;
        this.routingStrategy = routingStrategy;
        this.serializer = serializer;
    }

    @Override
    public Optional<Member> findDestination(CommandMessage<?> commandMessage) {
        return consistentHash.getMember(routingStrategy.getRoutingKey(commandMessage), commandMessage);
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
                        serviceInstance.getMetadata().containsKey(SERIALIZED_COMMAND_FILTER_CLASS_NAME) )
                .collect(Collectors.toSet());
        updateMemberships(allServiceInstances);
    }

    private void updateMemberships(Set<ServiceInstance> serviceInstances) {
        serviceInstances.forEach(serviceInstance -> {
            SimpleMember<URI> simpleMember = new SimpleMember<>(
                    serviceInstance.getServiceId().toUpperCase(),
                    serviceInstance.getUri(), member -> consistentHash.without(member));

            Map<String, String> serviceInstanceMetadata = serviceInstance.getMetadata();

            int loadFactor = Integer.parseInt(serviceInstanceMetadata.get(LOAD_FACTOR));
            SimpleSerializedObject<String> serializedObject = new SimpleSerializedObject<>(
                    serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER), String.class,
                    serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER_CLASS_NAME), null);
            CommandNameFilter commandNameFilter = serializer.deserialize(serializedObject);

            consistentHash = consistentHash.with(simpleMember, loadFactor, commandNameFilter);
        });
    }

}
