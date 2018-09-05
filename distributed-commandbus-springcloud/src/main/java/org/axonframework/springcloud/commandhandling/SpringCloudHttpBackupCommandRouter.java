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
import org.axonframework.commandhandling.distributed.ConsistentHashChangeListener;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Implementation of the {@link org.axonframework.springcloud.commandhandling.SpringCloudCommandRouter} which has a
 * backup mechanism to provide Message Routing Information for other nodes and to retrieve Message Routing Information
 * from other Axon nodes.
 * <p>
 * It is annotated with the {@link org.springframework.web.bind.annotation.RestController} and contains the
 * {@link org.springframework.web.bind.annotation.GetMapping} annotated
 * {@link SpringCloudHttpBackupCommandRouter#getLocalMessageRoutingInformation()} function to have a queryable place to
 * retrieve this node its Message Routing Information.
 * The default endpoint for this is {@code "/message-routing-information"}.
 * To configure this endpoint the "axon.distributed.spring-cloud.fallback-url" application property should be adjusted.
 * <p>
 * For retrieving the Message Routing Information from other Axon nodes, it uses a
 * {@link org.springframework.web.client.RestTemplate} to request Message Routing Information through an override of the
 * {@link SpringCloudHttpBackupCommandRouter#getMessageRoutingInformation(ServiceInstance)} function.
 *
 * @author Steven van Beelen
 * @since 3.1
 */
@RestController
@RequestMapping("${axon.distributed.spring-cloud.fallback-url:/message-routing-information}")
public class SpringCloudHttpBackupCommandRouter extends SpringCloudCommandRouter {

    private static final Logger logger = LoggerFactory.getLogger(SpringCloudHttpBackupCommandRouter.class);

    private static final Predicate<ServiceInstance> ACCEPT_ALL_INSTANCES_FILTER = serviceInstance -> true;

    private final RestTemplate restTemplate;
    private final String messageRoutingInformationEndpoint;
    private final MessageRoutingInformation unreachableService;

    private volatile MessageRoutingInformation messageRoutingInfo;

    /**
     * Initialize a {@link org.axonframework.commandhandling.distributed.CommandRouter} with the given {@link
     * org.springframework.cloud.client.discovery.DiscoveryClient} to update its own membership as a {@code
     * CommandRouter} and to create its own awareness of available nodes to send commands to in a {@link
     * org.axonframework.commandhandling.distributed.ConsistentHash}.
     * The {@code routingStrategy} is used to define the key based on which Command Messages are routed to their
     * respective handler nodes.
     * The {@link org.springframework.web.client.RestTemplate} is used as a backup mechanism to request another member's
     * {@link org.axonframework.springcloud.commandhandling.MessageRoutingInformation} with.
     * Uses a default {@code Predicate<ServiceInstance>} filter function which allows any
     * {@link org.springframework.cloud.client.ServiceInstance} through the update membership process.
     * Uses a default NoOp {@link org.axonframework.commandhandling.distributed.ConsistentHashChangeListener} which is
     * called if the ConsistentHash changed.
     *
     * @param discoveryClient                   The {@code DiscoveryClient} used to discovery and notify other nodes
     * @param localServiceInstance              A {@link org.springframework.cloud.client.serviceregistry.Registration}
     *                                          representing the local Service Instance of this application. Necessary
     *                                          to differentiate between other instances for correct message routing
     * @param routingStrategy                   The strategy for routing Commands to a Node
     * @param restTemplate                      The {@code RestTemplate} used to request another member's {@link
     *                                          org.axonframework.springcloud.commandhandling.MessageRoutingInformation}
     *                                          with.
     * @param messageRoutingInformationEndpoint The endpoint where to retrieve the another nodes message routing
     *                                          information from
     */
    @Autowired
    public SpringCloudHttpBackupCommandRouter(DiscoveryClient discoveryClient,
                                              Registration localServiceInstance,
                                              RoutingStrategy routingStrategy,
                                              RestTemplate restTemplate,
                                              @Value("${axon.distributed.spring-cloud.fallback-url:/message-routing-information}") String messageRoutingInformationEndpoint) {
        this(discoveryClient,
             localServiceInstance,
             routingStrategy,
             ACCEPT_ALL_INSTANCES_FILTER,
             restTemplate,
             messageRoutingInformationEndpoint);
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
     * The {@link org.springframework.web.client.RestTemplate} is used as a backup mechanism to request another member's
     * {@link org.axonframework.springcloud.commandhandling.MessageRoutingInformation} with.
     * * Uses a default NoOp {@link org.axonframework.commandhandling.distributed.ConsistentHashChangeListener} which is
     * called if the ConsistentHash changed.
     *
     * @param discoveryClient                   The {@code DiscoveryClient} used to discovery and notify other nodes
     * @param localServiceInstance              A {@link org.springframework.cloud.client.serviceregistry.Registration}
     *                                          representing the local Service Instance of this application. Necessary
     *                                          to differentiate between other instances for correct message routing
     * @param routingStrategy                   The strategy for routing Commands to a Node
     * @param serviceInstanceFilter             The {@code Predicate<ServiceInstance>} used to filter
     * @param restTemplate                      The {@code RestTemplate} used to request another member's {@link
     *                                          org.axonframework.springcloud.commandhandling.MessageRoutingInformation}
     *                                          with.
     * @param messageRoutingInformationEndpoint The endpoint where to retrieve the
     *                                          another nodes message routing
     *                                          information from
     */
    public SpringCloudHttpBackupCommandRouter(DiscoveryClient discoveryClient,
                                              Registration localServiceInstance,
                                              RoutingStrategy routingStrategy,
                                              Predicate<ServiceInstance> serviceInstanceFilter,
                                              RestTemplate restTemplate,
                                              String messageRoutingInformationEndpoint) {
        this(discoveryClient,
             localServiceInstance,
             routingStrategy,
             serviceInstanceFilter,
             ConsistentHashChangeListener.noOp(),
             restTemplate,
             messageRoutingInformationEndpoint);
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
     * The given {@code consistentHashChangeListener} is notified about changes in membership that affect routing of
     * messages.
     * The {@link org.springframework.web.client.RestTemplate} is used as a backup mechanism to request another member's
     * {@link org.axonframework.springcloud.commandhandling.MessageRoutingInformation} with.
     *
     * @param discoveryClient                   The {@code DiscoveryClient} used to discovery and notify other nodes
     * @param localServiceInstance              A {@link org.springframework.cloud.client.serviceregistry.Registration}
     *                                          representing the local Service Instance of this application. Necessary
     *                                          to differentiate between other instances for correct message routing
     * @param routingStrategy                   The strategy for routing Commands to a Node
     * @param serviceInstanceFilter             The {@code Predicate<ServiceInstance>} used to filter
     * @param consistentHashChangeListener      The callback to invoke when there is a change in the ConsistentHash
     * @param restTemplate                      The {@code RestTemplate} used to request another member's {@link
     *                                          org.axonframework.springcloud.commandhandling.MessageRoutingInformation}
     *                                          with.
     * @param messageRoutingInformationEndpoint The endpoint where to retrieve the
     *                                          another nodes message routing
     *                                          information from
     */
    public SpringCloudHttpBackupCommandRouter(DiscoveryClient discoveryClient,
                                              Registration localServiceInstance,
                                              RoutingStrategy routingStrategy,
                                              Predicate<ServiceInstance> serviceInstanceFilter,
                                              ConsistentHashChangeListener consistentHashChangeListener,
                                              RestTemplate restTemplate,
                                              String messageRoutingInformationEndpoint) {
        super(discoveryClient,
              localServiceInstance,
              routingStrategy,
              serviceInstanceFilter,
              consistentHashChangeListener);
        this.restTemplate = restTemplate;
        this.messageRoutingInformationEndpoint = messageRoutingInformationEndpoint;
        this.messageRoutingInfo = null;
        unreachableService = new MessageRoutingInformation(0, DenyAll.INSTANCE, serializer);
    }

    @Override
    public void updateMembership(int loadFactor, Predicate<? super CommandMessage<?>> commandFilter) {
        messageRoutingInfo = new MessageRoutingInformation(loadFactor, commandFilter, serializer);
        super.updateMembership(loadFactor, commandFilter);
    }

    /**
     * Get the local {@link MessageRoutingInformation}, thus the MessageRoutingInformation of the node this
     * CommandRouter is a part of. Can either be called directly or through a GET operation on the specified
     * {@code messageRoutingInformationEndpoint} of this node.
     *
     * @return The {@link MessageRoutingInformation} if the node this CommandRouter implementation is part of.
     */
    @GetMapping
    public MessageRoutingInformation getLocalMessageRoutingInformation() {
        return messageRoutingInfo;
    }

    @Override
    protected Optional<MessageRoutingInformation> getMessageRoutingInformation(ServiceInstance serviceInstance) {
        Optional<MessageRoutingInformation> defaultMessageRoutingInfo =
                super.getMessageRoutingInformation(serviceInstance);
        return defaultMessageRoutingInfo.isPresent() ?
                defaultMessageRoutingInfo : requestMessageRoutingInformation(serviceInstance);
    }

    private Optional<MessageRoutingInformation> requestMessageRoutingInformation(ServiceInstance serviceInstance) {
        Member member = buildMember(serviceInstance);
        if (member.local()) {
            return Optional.of(getLocalMessageRoutingInformation());
        }

        URI endpoint = member.getConnectionEndpoint(URI.class)
                             .orElseThrow(() -> new IllegalArgumentException(String.format(
                                     "No Connection Endpoint found in Member [%s] for protocol [%s] to send a " +
                                             "%s request to", member,
                                     URI.class, MessageRoutingInformation.class.getSimpleName()
                             )));
        URI destinationUri = buildURIForPath(endpoint, messageRoutingInformationEndpoint);

        try {
            ResponseEntity<MessageRoutingInformation> responseEntity = restTemplate.exchange(destinationUri,
                                                                                             HttpMethod.GET,
                                                                                             HttpEntity.EMPTY,
                                                                                             MessageRoutingInformation.class);

            return responseEntity.hasBody() ? Optional.of(responseEntity.getBody()) : Optional.empty();
        } catch (HttpClientErrorException e) {
            logger.info("Blacklisting Service [" + serviceInstance.getServiceId() + "], "
                                + "as requesting message routing information from it resulted in an exception.", e);
            return Optional.empty();
        } catch (Exception e) {
            logger.info("Failed to receive message routing information from Service ["
                                + serviceInstance.getServiceId() + "] due to an exception. "
                                + "Will temporarily set this instance to deny all incoming messages", e);
            return Optional.of(unreachableService);
        }
    }

    private static URI buildURIForPath(URI uri, String appendToPath) {
        return UriComponentsBuilder.fromUri(uri)
                                   .path(uri.getPath() + appendToPath)
                                   .build()
                                   .toUri();
    }
}
