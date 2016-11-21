package org.axonframework.commandhandling.distributed.spring;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.*;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
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
    private static final String COMMANDS = "commands";
    private static final String COMMA = ",";

    private DiscoveryClient discoveryClient;
    private RoutingStrategy routingStrategy;
    private Set<ServiceInstance> serviceInstances;
    private ConsistentHash consistentHash;


    public SpringCloudCommandRouter(DiscoveryClient discoveryClient, RoutingStrategy routingStrategy) {
        this.discoveryClient = discoveryClient;
        this.routingStrategy = routingStrategy;
        this.serviceInstances = new HashSet<>();
        this.consistentHash = new ConsistentHash();
    }

    /* TODO
    Now recreates the ConsistentHash for every command
    Should consider some mechanism which checks whether members have changed there state

    Additionally, the update done here also takes the localServiceInstance into account, which is currently updated on the updateMembership.
    Hence might lose the `updateMembership(...)` update in favour of the update done in `findDestination(...)`
     */
    @Override
    public Optional<Member> findDestination(CommandMessage<?> commandMessage) {
        LOGGER.info(String.format("findDestination for commandMessage [%s]", commandMessage));

        updateConsistentHash();

        return consistentHash.getMember(routingStrategy.getRoutingKey(commandMessage), commandMessage);
    }

    // Update ConsistentHash for possible updates in supported commands or new nodes
    private void updateConsistentHash() {
        Set<ServiceInstance> currentServiceInstances = discoveryClient.getServices().stream()
                .map(service -> discoveryClient.getInstances(service))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<ServiceInstance> storedServiceInstances = this.serviceInstances;

        currentServiceInstances.removeAll(storedServiceInstances);


        currentServiceInstances.forEach(this::updateConsistentHashWith);
    }

    @EventListener
    public void updateMemberships(HeartbeatEvent event) {
        System.out.println("TODO: Update consistentHash");
    }

    private void updateConsistentHashWith(ServiceInstance serviceInstance) {
        SimpleMember<URI> simpleMember = new SimpleMember<>(serviceInstance.getServiceId(), serviceInstance.getUri(), null);
        Map<String, String> serviceInstanceMetadata = serviceInstance.getMetadata();
        CommandNameFilter commandNameFilter = buildCommandNameFilter(serviceInstanceMetadata.get(COMMANDS));

        consistentHash = consistentHash.with(simpleMember, Integer.parseInt(serviceInstanceMetadata.get(LOAD_FACTOR)), commandNameFilter);
    }

    private CommandNameFilter buildCommandNameFilter(String commaSeparatedCommandsString) {
        return new CommandNameFilter(Arrays.stream(commaSeparatedCommandsString.split(COMMA)).collect(Collectors.toSet()));
    }

    @Override
    public void updateMembership(int loadFactor, Predicate<CommandMessage<?>> commandFilter) {
        LOGGER.info(String.format("updateMembership with loadFactor [%d] and commandFilter predicate [%s]", loadFactor, commandFilter));

        ServiceInstance localServiceInstance = this.discoveryClient.getLocalServiceInstance();
        Map<String, String> localServiceInstanceMetadata = localServiceInstance.getMetadata();
        localServiceInstanceMetadata.put(LOAD_FACTOR, Integer.toString(loadFactor));
        localServiceInstanceMetadata.put(COMMANDS, awkwardInfoStringRemoval(commandFilter.toString()));
    }

    /* TODO
    Preferably we wouldn't have to check for the correct substring to grab, but because the updateMembership function
    in the CommandRouter takes Predicates rather than command names, we have no other way to store them in the
    DiscoveryClient metadata
     */
    private String awkwardInfoStringRemoval(String notReallyCommandSeparatedCommandList) {
        String prependedCommandList = "CommandNameFilter{commandNames=[";
        String appendedToCommandList = "]}";

        int startCommandList = notReallyCommandSeparatedCommandList.indexOf(prependedCommandList) + prependedCommandList.length();
        int endCommandList = notReallyCommandSeparatedCommandList.indexOf(appendedToCommandList);

        return notReallyCommandSeparatedCommandList.substring(startCommandList, endCommandList);
    }

}
