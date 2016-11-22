package org.axonframework.commandhandling.distributed.spring;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.*;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SpringCloudCommandRouter implements CommandRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringCloudCommandRouter.class);

    private static final String LOAD_FACTOR = "loadFactor";
    private static final String SERIALIZED_COMMAND_FILTER = "serializedCommandFilter";
    private static final String SERIALIZED_COMMAND_FILTER_CLASS_NAME = "serializedCommandFilterClassName";

    private final DiscoveryClient discoveryClient;
    private final RoutingStrategy routingStrategy;
    private final Serializer serializer;
    private ConsistentHash consistentHash;


    public SpringCloudCommandRouter(DiscoveryClient discoveryClient, RoutingStrategy routingStrategy, Serializer serializer) {
        this.discoveryClient = discoveryClient;
        this.routingStrategy = routingStrategy;
        this.serializer = serializer;
        this.consistentHash = new ConsistentHash();
    }

    @Override
    public Optional<Member> findDestination(CommandMessage<?> commandMessage) {
        LOGGER.info(String.format("findDestination for commandMessage [%s]", commandMessage));

        return consistentHash.getMember(routingStrategy.getRoutingKey(commandMessage), commandMessage);
    }

    @Override
    public void updateMembership(int loadFactor, Predicate<CommandMessage<?>> commandFilter) {
        LOGGER.info(String.format("updateMembership with loadFactor [%d] and commandFilter predicate [%s]", loadFactor, commandFilter));

        ServiceInstance localServiceInstance = this.discoveryClient.getLocalServiceInstance();
        Map<String, String> localServiceInstanceMetadata = localServiceInstance.getMetadata();
        localServiceInstanceMetadata.put(LOAD_FACTOR, Integer.toString(loadFactor));
        SerializedObject<String> serializedCommandFilter = serializer.serialize(commandFilter, String.class);
        localServiceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER, serializedCommandFilter.getData());
        localServiceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER_CLASS_NAME, serializedCommandFilter.getType().getName());

        updateMemberships(Collections.singleton(localServiceInstance));
    }

    @EventListener
    public void updateMemberships(HeartbeatEvent event) {
        LOGGER.info(String.format("update ConsistentHash based on updateMemberships(...) for event [%s]", event));
        Set<ServiceInstance> allServiceInstances = discoveryClient.getServices().stream()
                .map(discoveryClient::getInstances)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        updateMemberships(allServiceInstances);
    }

    private void updateMemberships(Set<ServiceInstance> serviceInstances) {
        Set<Member> currentMembers = consistentHash.getMembers();
        serviceInstances.stream()
                .filter(serviceInstance -> !currentMembers.stream()
                        .filter(memberAlreadyRegistered(serviceInstance))
                        .findFirst()
                        .isPresent()
                ).forEach(this::updateConsistentHashWith);
    }

    // TODO implement a predicate to match on name, URI and command filter
    private Predicate<Member> memberAlreadyRegistered(ServiceInstance serviceInstance) {
//        return member -> serviceInstance.getServiceId().equals(member.name());
        return member -> false;
    }

    private void updateConsistentHashWith(ServiceInstance newServiceInstance) {
        SimpleMember<URI> simpleMember = new SimpleMember<>(newServiceInstance.getServiceId(),
                newServiceInstance.getUri(), member -> consistentHash.without(member));

        Map<String, String> serviceInstanceMetadata = newServiceInstance.getMetadata();

        int loadFactor = Integer.parseInt(serviceInstanceMetadata.get(LOAD_FACTOR));
        SimpleSerializedObject<String> serializedObject = new SimpleSerializedObject<>(
                serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER), String.class,
                serviceInstanceMetadata.get(SERIALIZED_COMMAND_FILTER_CLASS_NAME), null);
        CommandNameFilter commandNameFilter = serializer.deserialize(serializedObject);

        consistentHash = consistentHash.with(simpleMember, loadFactor, commandNameFilter);
    }

}
