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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandRouter;
import org.axonframework.commandhandling.distributed.ConsistentHash;
import org.axonframework.commandhandling.distributed.ConsistentHashChangeListener;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.SimpleMember;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link CommandRouter} implementation which uses Spring Cloud's {@link DiscoveryClient}s to propagate its
 * Command Message Routing Information, and to discover other Axon nodes and retrieve their Message Routing Information.
 * It does so by utilizing the metadata contained in a {@link ServiceInstance} for storing the Message Routing
 * Information in. Other nodes discovered through the DiscoveryClient system which do not contain any of the required
 * Message Routing Information fields will be black listed, so not to perform any unneeded additional checks on that
 * node.
 * <p>
 * The {@code localServiceInstance} is added for correct deviation between the local instance and the instances
 * retrieved from the DiscoveryClient. A {@link RoutingStrategy} is in place to decide which instance will handle a
 * given Command Message. Lastly, a {@link Predicate} of generic type {@link ServiceInstance} can be provided to filter
 * out specific service instances which need to be disregarded, and a {@link ConsistentHashChangeListener} is
 * configurable to notify if the memberships have been updated.
 *
 * @author Steven van Beelen
 * @since 3.0
 */
public class SpringCloudCommandRouter implements CommandRouter {

    private static final Logger logger = LoggerFactory.getLogger(SpringCloudCommandRouter.class);

    private static final String LOAD_FACTOR = "loadFactor";
    private static final String SERIALIZED_COMMAND_FILTER = "serializedCommandFilter";
    private static final String SERIALIZED_COMMAND_FILTER_CLASS_NAME = "serializedCommandFilterClassName";

    protected final XStreamSerializer serializer = new XStreamSerializer();

    private final DiscoveryClient discoveryClient;
    private final Registration localServiceInstance;
    private final RoutingStrategy routingStrategy;
    private final Predicate<ServiceInstance> serviceInstanceFilter;
    private final ConsistentHashChangeListener consistentHashChangeListener;

    private final AtomicReference<ConsistentHash> atomicConsistentHash = new AtomicReference<>(new ConsistentHash());
    private final Set<ServiceInstance> blackListedServiceInstances = new HashSet<>();

    private volatile boolean registered = false;

    /**
     * Instantiate a {@link SpringCloudCommandRouter} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link DiscoveryClient}, {@code localServiceInstance} of type {@link Registration},
     * {@link RoutingStrategy}, {@code serviceInstanceFilter} and {@link ConsistentHashChangeListener} are not
     * {@code null}, and will throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SpringCloudCommandRouter} instance
     */
    protected SpringCloudCommandRouter(Builder builder) {
        builder.validate();
        discoveryClient = builder.discoveryClient;
        localServiceInstance = builder.localServiceInstance;
        routingStrategy = builder.routingStrategy;
        serviceInstanceFilter = builder.serviceInstanceFilter;
        consistentHashChangeListener = builder.consistentHashChangeListener;
    }

    /**
     * Builder class to instantiate a {@link SpringCloudCommandRouter}.
     * <p>
     * The {@code serviceInstanceFilter} is defaulted to the
     * {@link SpringCloudCommandRouter#serviceInstanceMetadataContainsMessageRoutingInformation} function, and the
     * {@link ConsistentHashChangeListener} to a no-op solution.
     * The {@link DiscoveryClient}, {@code localServiceInstance} of type {@link Registration} and
     * {@link RoutingStrategy} are <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link SpringCloudCommandRouter}
     */
    public static Builder builder() {
        return new Builder();
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

    @Override
    public Optional<Member> findDestination(CommandMessage<?> commandMessage) {
        return atomicConsistentHash.get().getMember(routingStrategy.getRoutingKey(commandMessage), commandMessage);
    }

    @Override
    public void updateMembership(int loadFactor, Predicate<? super CommandMessage<?>> commandFilter) {
        Map<String, String> localServiceInstanceMetadata = localServiceInstance.getMetadata();
        localServiceInstanceMetadata.put(LOAD_FACTOR, Integer.toString(loadFactor));
        SerializedObject<String> serializedCommandFilter = serializer.serialize(commandFilter, String.class);
        localServiceInstanceMetadata.put(SERIALIZED_COMMAND_FILTER, serializedCommandFilter.getData());
        localServiceInstanceMetadata.put(
                SERIALIZED_COMMAND_FILTER_CLASS_NAME, serializedCommandFilter.getType().getName()
        );

        updateMembershipForServiceInstance(localServiceInstance, atomicConsistentHash)
                .ifPresent(consistentHashChangeListener::onConsistentHashChanged);
    }

    /**
     * Update the local member and all the other remote members known by the
     * {@link org.springframework.cloud.client.discovery.DiscoveryClient} to be able to have an as up-to-date awareness
     * of which actions which members can handle. This function is automatically triggered by an (unused)
     * {@link org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent}. Upon this event we may assume
     * that the application has fully start up. Because of this we can update the local member with the correct name and
     * {@link java.net.URI}, as initially these were not provided by the
     * {@link org.springframework.cloud.client.serviceregistry.Registration} yet.
     *
     * @param event an unused {@link org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent}, serves
     *              as a trigger for this function
     * @see SpringCloudCommandRouter#buildMember(ServiceInstance)
     */
    @EventListener
    @SuppressWarnings("UnusedParameters")
    public void resetLocalMembership(InstanceRegisteredEvent event) {
        registered = true;
        Member startUpPhaseLocalMember =
                atomicConsistentHash.get().getMembers().stream()
                                    .filter(Member::local)
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalStateException(
                                            "There should be no scenario where the local member does not exist."
                                    ));

        if (logger.isDebugEnabled()) {
            logger.debug("Resetting local membership for [{}].", startUpPhaseLocalMember);
        }

        updateMemberships();
        atomicConsistentHash.updateAndGet(consistentHash -> consistentHash.without(startUpPhaseLocalMember));
    }

    /**
     * Update the memberships of all nodes known by the
     * {@link org.springframework.cloud.client.discovery.DiscoveryClient} to be able to have an as up-to-date awareness
     * of which actions which members can handle. This function is automatically triggered by
     * an (unused) {@link org.springframework.cloud.client.discovery.event.HeartbeatEvent}.
     *
     * @param event an unused {@link org.springframework.cloud.client.discovery.event.HeartbeatEvent}, serves as a
     *              trigger for this function
     */
    @EventListener
    @SuppressWarnings("UnusedParameters")
    public void updateMemberships(HeartbeatEvent event) {
        updateMemberships();
    }

    private void updateMemberships() {
        AtomicReference<ConsistentHash> updatedConsistentHash = new AtomicReference<>(new ConsistentHash());

        List<ServiceInstance> instances = discoveryClient.getServices().stream()
                                                         .map(discoveryClient::getInstances)
                                                         .flatMap(Collection::stream)
                                                         .filter(serviceInstanceFilter)
                                                         .collect(Collectors.toList());

        cleanBlackList(instances);

        instances.stream()
                 .filter(this::ifNotBlackListed)
                 .forEach(serviceInstance -> updateMembershipForServiceInstance(serviceInstance,
                                                                                updatedConsistentHash));

        ConsistentHash newConsistentHash = updatedConsistentHash.get();
        atomicConsistentHash.set(newConsistentHash);
        consistentHashChangeListener.onConsistentHashChanged(newConsistentHash);
    }

    private void cleanBlackList(List<ServiceInstance> instances) {
        blackListedServiceInstances.removeIf(
                blackListedInstance -> instances.stream().noneMatch(instance -> equals(instance, blackListedInstance))
        );
    }

    private boolean ifNotBlackListed(ServiceInstance serviceInstance) {
        return blackListedServiceInstances.stream()
                                          .noneMatch(blackListedServiceInstance -> equals(serviceInstance,
                                                                                          blackListedServiceInstance));
    }

    /**
     * Implementation of the {@link org.springframework.cloud.client.ServiceInstance} in some cases do no have an
     * {@code equals()} implementation. Thus we provide our own {@code equals()} function to match a given
     * {@code blackListedServiceInstance} with another given {@code serviceInstance}.
     * The match is done on the service id, host and port.
     *
     * @param serviceInstance            A {@link org.springframework.cloud.client.ServiceInstance} to compare with the
     *                                   given {@code blackListedServiceInstance}
     * @param blackListedServiceInstance A {@link org.springframework.cloud.client.ServiceInstance} to compare with the
     *                                   given {@code serviceInstance}
     * @return True if both instances match on the service id, host and port, and false if they do not
     */
    @SuppressWarnings("SimplifiableIfStatement")
    private boolean equals(ServiceInstance serviceInstance, ServiceInstance blackListedServiceInstance) {
        if (serviceInstance == blackListedServiceInstance) {
            return true;
        }
        if (blackListedServiceInstance == null) {
            return false;
        }
        return Objects.equals(serviceInstance.getServiceId(), blackListedServiceInstance.getServiceId())
                && Objects.equals(serviceInstance.getHost(), blackListedServiceInstance.getHost())
                && Objects.equals(serviceInstance.getPort(), blackListedServiceInstance.getPort());
    }

    private Optional<ConsistentHash> updateMembershipForServiceInstance(ServiceInstance serviceInstance,
                                                                        AtomicReference<ConsistentHash> atomicConsistentHash) {
        if (logger.isDebugEnabled()) {
            logger.debug("Updating membership for service instance: [{}]", serviceInstance);
        }

        Member member = buildMember(serviceInstance);

        Optional<MessageRoutingInformation> optionalMessageRoutingInfo = getMessageRoutingInformation(serviceInstance);

        if (optionalMessageRoutingInfo.isPresent()) {
            MessageRoutingInformation messageRoutingInfo = optionalMessageRoutingInfo.get();
            return Optional.of(atomicConsistentHash.updateAndGet(
                    consistentHash -> consistentHash.with(member,
                                                          messageRoutingInfo.getLoadFactor(),
                                                          messageRoutingInfo.getCommandFilter(serializer))
            ));
        } else {
            logger.info(
                    "Black listed ServiceInstance [{}] under host [{}] and port [{}] since we could not retrieve the "
                            + "required Message Routing Information from it.",
                    serviceInstance.getServiceId(), serviceInstance.getHost(), serviceInstance.getPort()
            );
            blackListedServiceInstances.add(serviceInstance);
        }
        return Optional.empty();
    }

    /**
     * Instantiate a {@link Member} of type {@link java.net.URI} based on the provided {@code serviceInstance}.
     * This Member is later used to send, for example, Command messages to.
     * </p>
     * A deviation is made between a local and a remote member, since if local is selected to handle the command, the
     * local CommandBus may be leveraged. The check if a {@link org.springframework.cloud.client.ServiceInstance} is
     * local is based on two potential situations: 1) the given {@code serviceInstance} is identical to the
     * {@code localServiceInstance} thus making it local or 2) the URI of the given {@code serviceInstance} is identical
     * to the URI of the {@code localServiceInstance}.
     * We take this route because we've identified that several Spring Cloud implementation do not contain any URI
     * information during the start up phase and as a side effect will throw exception if the URI is requested from it.
     * We thus return a simplified Member for the {@code localServiceInstance} to not trigger this exception.
     *
     * @param serviceInstance A {@link org.springframework.cloud.client.ServiceInstance} to build a {@link Member} for
     * @return A {@link Member} based on the contents of the provided {@code serviceInstance}
     */
    protected Member buildMember(ServiceInstance serviceInstance) {
        return isLocalServiceInstance(serviceInstance)
                ? buildLocalMember(serviceInstance)
                : buildRemoteMember(serviceInstance);
    }

    private boolean isLocalServiceInstance(ServiceInstance serviceInstance) {
        return serviceInstance.equals(localServiceInstance)
                || Objects.equals(serviceInstance.getUri(), localServiceInstance.getUri());
    }

    private Member buildLocalMember(ServiceInstance localServiceInstance) {
        String localServiceId = localServiceInstance.getServiceId();
        URI emptyEndpoint = null;
        //noinspection ConstantConditions | added null variable for clarity
        return registered
                ? new SimpleMember<>(buildSimpleMemberName(localServiceId, localServiceInstance.getUri()),
                                     localServiceInstance.getUri(),
                                     SimpleMember.LOCAL_MEMBER,
                                     this::suspect)
                : new SimpleMember<>(localServiceId.toUpperCase() + "[LOCAL]",
                                     emptyEndpoint,
                                     SimpleMember.LOCAL_MEMBER,
                                     this::suspect);
    }

    private Member buildRemoteMember(ServiceInstance remoteServiceInstance) {
        URI remoteServiceUri = remoteServiceInstance.getUri();
        return new SimpleMember<>(buildSimpleMemberName(remoteServiceInstance.getServiceId(), remoteServiceUri),
                                  remoteServiceUri,
                                  SimpleMember.REMOTE_MEMBER,
                                  this::suspect);
    }

    private String buildSimpleMemberName(String serviceId, URI serviceUri) {
        return serviceId.toUpperCase() + "[" + serviceUri + "]";
    }

    private ConsistentHash suspect(Member member) {
        ConsistentHash newConsistentHash =
                atomicConsistentHash.updateAndGet(consistentHash -> consistentHash.without(member));
        consistentHashChangeListener.onConsistentHashChanged(newConsistentHash);
        return newConsistentHash;
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
     * Builder class to instantiate a {@link SpringCloudCommandRouter}.
     * <p>
     * The {@code serviceInstanceFilter} is defaulted to the
     * {@link SpringCloudCommandRouter#serviceInstanceMetadataContainsMessageRoutingInformation} function, and the
     * {@link ConsistentHashChangeListener} to a no-op solution.
     * The {@link DiscoveryClient}, {@code localServiceInstance} of type {@link Registration} and
     * {@link RoutingStrategy} are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private DiscoveryClient discoveryClient;
        private Registration localServiceInstance;
        private RoutingStrategy routingStrategy;
        private Predicate<ServiceInstance> serviceInstanceFilter =
                SpringCloudCommandRouter::serviceInstanceMetadataContainsMessageRoutingInformation;
        private ConsistentHashChangeListener consistentHashChangeListener = ConsistentHashChangeListener.noOp();

        /**
         * Sets the {@link DiscoveryClient} used to discovery and notify other nodes. Used to update its own membership
         * as a {@code CommandRouter} and to create its awareness of available nodes to send commands to.
         *
         * @param discoveryClient the {@link DiscoveryClient} used to discovery and notify other nodes
         * @return the current Builder instance, for a fluent interfacing
         */
        public Builder discoveryClient(DiscoveryClient discoveryClient) {
            assertNonNull(discoveryClient, "DiscoveryClient may not be null");
            this.discoveryClient = discoveryClient;
            return this;
        }

        /**
         * Sets the {@link Registration}, representing the local Service Instance of this application.
         * Necessary to differentiate other instances from the local instance to ensure correct message routing.
         *
         * @param localServiceInstance the {@link Registration}, representing the local Service Instance of this
         *                             application
         * @return the current Builder instance, for a fluent interfacing
         */
        public Builder localServiceInstance(Registration localServiceInstance) {
            assertNonNull(localServiceInstance, "Registration may not be null");
            this.localServiceInstance = localServiceInstance;
            return this;
        }

        /**
         * Sets the {@link RoutingStrategy} used to define the key on which Command Messages are routed to their
         * respective nodes.
         *
         * @param routingStrategy the {@link RoutingStrategy} used to define the key on which Command Messages are
         *                        routed to their respective nodes
         * @return the current Builder instance, for a fluent interfacing
         */
        public Builder routingStrategy(RoutingStrategy routingStrategy) {
            assertNonNull(routingStrategy, "RoutingStrategy may not be null");
            this.routingStrategy = routingStrategy;
            return this;
        }

        /**
         * Sets a {@link Predicate} of generic type {@link ServiceInstance}, used to filter out ServiceInstances from
         * the membership update loop. Defaults to the
         * {@link SpringCloudCommandRouter#serviceInstanceMetadataContainsMessageRoutingInformation(ServiceInstance)},
         * which filters any instances missing message routing information key-value pairs in their metadata.
         *
         * @param serviceInstanceFilter the {@link Predicate} of generic type {@link ServiceInstance}, used to filter
         *                              out ServiceInstances from the membership update loop
         * @return the current Builder instance, for a fluent interfacing
         */
        public Builder serviceInstanceFilter(Predicate<ServiceInstance> serviceInstanceFilter) {
            assertNonNull(serviceInstanceFilter, "ServiceInstanceFilter may not be null");
            this.serviceInstanceFilter = serviceInstanceFilter;
            return this;
        }

        /**
         * Sets the {@link ConsistentHashChangeListener} which is notified when a change in membership has
         * <em>potentially</em> caused a change in the consistent hash. Defaults to a no-op solution.
         *
         * @param consistentHashChangeListener the {@link ConsistentHashChangeListener} which is notified when a change
         *                                     in membership has <em>potentially</em> caused a change in the consistent
         *                                     hash
         * @return the current Builder instance, for a fluent interfacing
         */
        public Builder consistentHashChangeListener(ConsistentHashChangeListener consistentHashChangeListener) {
            assertNonNull(consistentHashChangeListener, "ConsistentHashChangeListener may not be null");
            this.consistentHashChangeListener = consistentHashChangeListener;
            return this;
        }

        /**
         * Initializes a {@link SpringCloudCommandRouter} as specified through this Builder.
         *
         * @return a {@link SpringCloudCommandRouter} as specified through this Builder
         */
        public SpringCloudCommandRouter build() {
            return new SpringCloudCommandRouter(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonNull(discoveryClient, "The DiscoveryClient is a hard requirement and should be provided");
            assertNonNull(localServiceInstance, "The Registration is a hard requirement and should be provided");
            assertNonNull(routingStrategy, "The RoutingStrategy is a hard requirement and should be provided");
            assertNonNull(serviceInstanceFilter,
                          "The ServiceInstanceFilter is a hard requirement and should be provided");
            assertNonNull(consistentHashChangeListener,
                          "The ConsistentHashChangeListener is a hard requirement and should be provided");
        }
    }
}
