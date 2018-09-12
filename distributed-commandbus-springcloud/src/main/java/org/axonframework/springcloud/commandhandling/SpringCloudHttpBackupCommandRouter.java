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
import org.axonframework.common.AxonConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

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
     * Instantiate a {@link SpringCloudHttpBackupCommandRouter} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link RestTemplate} is not {@code null} and that the
     * {@code messageRoutingInformationEndpoint} is not {@code null} and empty. This assertion will throw an
     * {@link AxonConfigurationException} if either of them asserts to {@code true}. All asserts performed by
     * the {@link SpringCloudCommandRouter.Builder} are also taken into account with identical consequences.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SpringCloudHttpBackupCommandRouter} instance
     */
    protected SpringCloudHttpBackupCommandRouter(Builder builder) {
        super(builder);
        this.restTemplate = builder.restTemplate;
        this.messageRoutingInformationEndpoint = builder.messageRoutingInformationEndpoint;
        messageRoutingInfo = null;
        unreachableService = new MessageRoutingInformation(0, DenyAll.INSTANCE, serializer);
    }

    /**
     * Builder class to instantiate a {@link SpringCloudHttpBackupCommandRouter}.
     * <p>
     * The {@code serviceInstanceFilter} is defaulted to a {@link Predicate} which always returns {@code true}, the
     * {@link ConsistentHashChangeListener} to a no-op solution and the {@code messageRoutingInformationEndpoint} to
     * {@code "/message-routing-information"}.
     * The {@link DiscoveryClient}, {@code localServiceInstance} of type {@link Registration}, the
     * {@link RoutingStrategy}, {@link RestTemplate} and {@code messageRoutingInformationEndpoint} are <b>hard
     * requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link SpringCloudHttpBackupCommandRouter}
     */
    public static Builder builder() {
        return new Builder();
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

            return Optional.ofNullable(responseEntity.getBody());
        } catch (HttpClientErrorException e) {
            logger.info("Blacklisting Service [" + serviceInstance.getServiceId() + "], "
                + "as requesting message routing information from it resulted in an exception.",
                logger.isDebugEnabled() ? e : null);
            return Optional.empty();
        } catch (Exception e) {
            logger.info("Failed to receive message routing information from Service ["
                                + serviceInstance.getServiceId() + "] due to an exception. "
                                + "Will temporarily set this instance to deny all incoming messages", logger.isDebugEnabled()?e:null);
            return Optional.of(unreachableService);
        }
    }

    private static URI buildURIForPath(URI uri, String appendToPath) {
        return UriComponentsBuilder.fromUri(uri)
                                   .path(uri.getPath() + appendToPath)
                                   .build()
                                   .toUri();
    }

    /**
     * Builder class to instantiate a {@link SpringCloudHttpBackupCommandRouter}.
     * <p>
     * The {@code serviceInstanceFilter} is defaulted to a {@link Predicate} which always returns {@code true}, the
     * {@link ConsistentHashChangeListener} to a no-op solution and the {@code messageRoutingInformationEndpoint} to
     * {@code "/message-routing-information"}.
     * The {@link DiscoveryClient}, {@code localServiceInstance} of type {@link Registration}, the
     * {@link RoutingStrategy}, {@link RestTemplate} and {@code messageRoutingInformationEndpoint} are <b>hard
     * requirements</b> and as such should be provided.
     */
    public static class Builder extends SpringCloudCommandRouter.Builder {

        private RestTemplate restTemplate;
        private String messageRoutingInformationEndpoint = "/message-routing-information";

        public Builder() {
            serviceInstanceFilter(ACCEPT_ALL_INSTANCES_FILTER);
        }

        @Override
        public Builder discoveryClient(DiscoveryClient discoveryClient) {
            super.discoveryClient(discoveryClient);
            return this;
        }

        @Override
        public Builder localServiceInstance(Registration localServiceInstance) {
            super.localServiceInstance(localServiceInstance);
            return this;
        }

        @Override
        public Builder routingStrategy(RoutingStrategy routingStrategy) {
            super.routingStrategy(routingStrategy);
            return this;
        }

        @Override
        public Builder serviceInstanceFilter(
                Predicate<ServiceInstance> serviceInstanceFilter) {
            super.serviceInstanceFilter(serviceInstanceFilter);
            return this;
        }

        @Override
        public Builder consistentHashChangeListener(ConsistentHashChangeListener consistentHashChangeListener) {
            super.consistentHashChangeListener(consistentHashChangeListener);
            return this;
        }

        /**
         * Sets the {@link RestTemplate} used as the backup mechanism to request another member's
         * {@link MessageRoutingInformation} with.
         *
         * @param restTemplate the {@link RestTemplate} used as the backup mechanism to request another member's
         *                     {@link MessageRoutingInformation} with.
         * @return the current Builder instance, for a fluent interfacing
         */
        public Builder restTemplate(RestTemplate restTemplate) {
            assertNonNull(restTemplate, "RestTemplate may not be null");
            this.restTemplate = restTemplate;
            return this;
        }

        /**
         * Sets the {@code messageRoutingInformationEndpoint} of type {@link String}, which is the endpoint where to
         * retrieve the another nodes message routing information from. Defaults to endpoint
         * {@code "/message-routing-information"}.
         *
         * @param messageRoutingInformationEndpoint the endpoint where to retrieve the another nodes message routing
         *                                          information from
         * @return the current Builder instance, for a fluent interfacing
         */
        public Builder messageRoutingInformationEndpoint(String messageRoutingInformationEndpoint) {
            assertMessageRoutingInfoEndpoint(messageRoutingInformationEndpoint,
                                             "The messageRoutingInformationEndpoint may not be null or empty");
            this.messageRoutingInformationEndpoint = messageRoutingInformationEndpoint;
            return this;
        }

        /**
         * Initializes a {@link SpringCloudHttpBackupCommandRouter} as specified through this Builder.
         *
         * @return a {@link SpringCloudHttpBackupCommandRouter} as specified through this Builder
         */
        public SpringCloudHttpBackupCommandRouter build() {
            return new SpringCloudHttpBackupCommandRouter(this);
        }

        @Override
        protected void validate() {
            super.validate();
            assertNonNull(restTemplate, "The RestTemplate is a hard requirement and should be provided");
            assertMessageRoutingInfoEndpoint(
                    messageRoutingInformationEndpoint,
                    "The messageRoutingInformationEndpoint is a hard requirement and should be provided"
            );
        }

        private void assertMessageRoutingInfoEndpoint(String messageRoutingInfoEndpoint, String exceptionMessage) {
            assertThat(messageRoutingInfoEndpoint, name -> Objects.nonNull(name) && !"".equals(name), exceptionMessage);
        }
    }
}
