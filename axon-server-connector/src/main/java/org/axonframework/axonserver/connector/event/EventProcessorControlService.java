/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.admin.AdminChannel;
import io.axoniq.axonserver.connector.control.ControlChannel;
import io.axoniq.axonserver.connector.control.ProcessorInstructionHandler;
import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
import io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessor;
import org.axonframework.common.lifecycle.Phase;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toMap;

/**
 * Service that listens to {@link PlatformOutboundInstruction PlatformOutboundInstructions} to control
 * {@link EventProcessor EventProcessors} when requested by Axon Server.
 * <p>
 * Will delegate the calls to the {@link AxonServerConnectionManager} for further processing.
 *
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 4.0.0
 */
@Internal
public class EventProcessorControlService {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessorControlService.class);

    private final AxonServerConnectionManager axonServerConnectionManager;
    private final Configuration configuration;
    private final String context;
    private final Map<String, AxonServerConfiguration.Eventhandling.ProcessorSettings> processorConfig;

    /**
     * Initialize an {@code EventProcessorControlService}.
     * <p>
     * This service adds processor instruction handlers to the {@link ControlChannel} of the given {@code context}.
     * Doing so ensures operation like the {@link EventProcessor#start() start} and
     * {@link EventProcessor#shutdown() shutdown} can be triggered through Axon Server. Furthermore, it sets the
     * configured load balancing strategies through the {@link AdminChannel} of the {@code context}.
     *
     * @param configuration     The {@link EventProcessor} configuration of this application, used to retrieve the
     *                          registered event processors from.
     * @param connectionManager A {@link AxonServerConnectionManager} from which to retrieve the {@link ControlChannel}
     *                          and {@link AdminChannel}.
     * @param context           The context of this application instance to retrieve the {@link ControlChannel} and
     *                          {@link AdminChannel} for.
     * @param processorConfig   The processor configuration from the {@link AxonServerConfiguration}, used to (for
     *                          example) retrieve the load balancing strategies from.
     */
    public EventProcessorControlService(
            @Nonnull Configuration configuration,
            @Nonnull AxonServerConnectionManager connectionManager,
            @Nonnull String context,
            @Nonnull Map<String, AxonServerConfiguration.Eventhandling.ProcessorSettings> processorConfig
    ) {
        this.axonServerConnectionManager = Objects.requireNonNull(
                connectionManager, "The Axon Server Connection Manager must not be null."
        );
        this.configuration = Objects.requireNonNull(configuration, "The Configuration must not be null.");
        this.context = Objects.requireNonNull(context, "The Context must not be null.");
        this.processorConfig = Objects.requireNonNull(processorConfig, "The Processor Configuration must not be null.");
    }

    /**
     * Registers {@link EventProcessor} instruction handlers to the {@link ControlChannel} for the configured
     * {@code context} and set the load balancing strategies through the {@link AdminChannel} for the configured
     * {@code context}.
     * <p>
     * The registration is performed in {@link Phase#INBOUND_EVENT_CONNECTORS} phase, to ensure the event processors
     * this service provides control over have been started.
     */
    public void start() {
        Map<String, EventProcessor> eventProcessors = configuration.getComponents(EventProcessor.class);

        AxonServerConnection connection = axonServerConnectionManager.getConnection(context);
        registerInstructionHandlers(connection, eventProcessors);
        setLoadBalancingStrategies(connection, eventProcessors.keySet());
    }

    private void registerInstructionHandlers(@Nonnull AxonServerConnection connection,
                                             @Nonnull Map<String, EventProcessor> eventProcessors) {
        ControlChannel controlChannel = connection.controlChannel();
        eventProcessors.forEach((name, processor) -> controlChannel.registerEventProcessor(
                name, infoSupplier(processor), new AxonProcessorInstructionHandler(processor, name)
        ));
    }

    @Nonnull
    private Supplier<EventProcessorInfo> infoSupplier(@Nonnull EventProcessor processor) {
        if (processor instanceof StreamingEventProcessor streamingProcessor) {
            return () -> EventProcessorInfoUtils.describeStreaming(streamingProcessor);
        } else if (processor instanceof SubscribingEventProcessor subscribingProcessor) {
            return () -> EventProcessorInfoUtils.describeSubscribing(subscribingProcessor);
        } else {
            return () -> EventProcessorInfoUtils.describeUnknown(processor);
        }
    }

    private void setLoadBalancingStrategies(AxonServerConnection connection, Set<String> processorNames) {
        AdminChannel adminChannel = connection.adminChannel();

        Map<String, String> strategiesPerProcessor =
                processorConfig.entrySet()
                               .stream()
                               .filter(entry -> {
                                   if (!processorNames.contains(entry.getKey())) {
                                       logger.info(
                                               "Event Processor [{}] is not a registered. Please check the name or register the Event Processor.",
                                               entry.getKey()
                                       );
                                       return false;
                                   }
                                   return true;
                               })
                               .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().getLoadBalancingStrategy()));

        strategiesPerProcessor.forEach((processorName, strategy) -> {
            Optional<String> optionalIdentifier = processorTokenStoreOrGlobal(processorName);
            if (optionalIdentifier.isEmpty()) {
                logger.warn(
                        "Cannot find token store identifier for processor [{}]. Load balancing cannot be configured without this identifier.",
                        processorName
                );
                return;
            }
            String tokenStoreIdentifier = optionalIdentifier.get();

            adminChannel.loadBalanceEventProcessor(processorName, tokenStoreIdentifier, strategy)
                        .whenComplete((r, e) -> {
                            if (e == null) {
                                logger.debug("Successfully requested to load balance processor [{}] with strategy [{}].",
                                             processorName, strategy);
                                return;
                            }
                            logger.warn("Requesting to load balance processor [{}] with strategy [{}] failed.",
                                        processorName, strategy, e);
                        });
            if (processorConfig.get(processorName).isAutomaticBalancing()) {
                adminChannel.setAutoLoadBalanceStrategy(processorName, tokenStoreIdentifier, strategy)
                            .whenComplete((r, e) -> {
                                if (e == null) {
                                    logger.debug("Successfully requested to automatically balance processor [{}]"
                                                         + " with strategy [{}].", processorName, strategy);
                                    return;
                                }
                                logger.warn(
                                        "Requesting to automatically balance processor [{}] with strategy [{}] failed.",
                                        processorName, strategy, e
                                );
                            });
            }
        });
    }

    private Optional<String> processorTokenStoreOrGlobal(String processorName) {
        Optional<Configuration> moduleConfiguration = configuration.getModuleConfiguration(processorName);

        Optional<TokenStore> tokenStore = moduleConfiguration
                .flatMap(m -> m.getOptionalComponent(TokenStore.class, "TokenStore[" + processorName + "]"))
                .or(() -> configuration.getOptionalComponent(TokenStore.class));
        if (tokenStore.isEmpty()) {
            logger.warn(
                    "Cannot find TokenStore for processor [{}]. Please ensure the processor module is properly configured.",
                    processorName);
            return Optional.empty();
        }

        var unitOfWorkFactory = moduleConfiguration.flatMap(m -> m.getOptionalComponent(UnitOfWorkFactory.class, "UnitOfWorkFactory[" + processorName + "]"))
                                                   .or(() -> configuration.getOptionalComponent(UnitOfWorkFactory.class));
        if (unitOfWorkFactory.isEmpty()) {
            logger.warn(
                    "Cannot find UnitOfWorkFactory for processor [{}]. Please ensure the processor module is properly configured.",
                    processorName);
            return Optional.empty();
        }

        var unitOfWork = unitOfWorkFactory.get().create();
        return Optional.of(FutureUtils.joinAndUnwrap(unitOfWork.executeWithResult(ctx -> tokenStore.get().retrieveStorageIdentifier(ctx))));
    }

    protected record AxonProcessorInstructionHandler(
            EventProcessor processor,
            String name
    ) implements ProcessorInstructionHandler {

        @Override
        public CompletableFuture<Boolean> releaseSegment(int segmentId) {
            try {
                if (!(processor instanceof StreamingEventProcessor)) {
                    logger.info("Release segment requested for processor [{}] which is not a Streaming Event Processor",
                                name);
                    return CompletableFuture.completedFuture(false);
                } else {
                    ((StreamingEventProcessor) processor).releaseSegment(segmentId);
                }
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> splitSegment(int segmentId) {
            try {
                if (!(processor instanceof StreamingEventProcessor)) {
                    logger.info("Split segment requested for processor [{}] which is not a Streaming Event Processor",
                                name);
                    return CompletableFuture.completedFuture(false);
                } else {
                    return ((StreamingEventProcessor) processor)
                            .splitSegment(segmentId)
                            .thenApply(result -> {
                                if (Boolean.TRUE.equals(result)) {
                                    logger.info("Successfully split segment [{}] of processor [{}]",
                                                segmentId, name);
                                } else {
                                    logger.warn("Was not able to split segment [{}] for processor [{}]",
                                                segmentId, name);
                                }
                                return result;
                            });
                }
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public CompletableFuture<Boolean> mergeSegment(int segmentId) {
            try {
                if (!(processor instanceof StreamingEventProcessor)) {
                    logger.warn(
                            "Merge segment request received for processor [{}] which is not a Streaming Event Processor",
                            name);
                    return CompletableFuture.completedFuture(false);
                } else {
                    return ((StreamingEventProcessor) processor)
                            .mergeSegment(segmentId)
                            .thenApply(result -> {
                                if (Boolean.TRUE.equals(result)) {
                                    logger.info("Successfully merged segment [{}] of processor [{}]",
                                                segmentId, name);
                                } else {
                                    logger.warn("Was not able to merge segment [{}] for processor [{}]",
                                                segmentId, name);
                                }
                                return result;
                            });
                }
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public CompletableFuture<Void> pauseProcessor() {
            return processor.shutdown();
        }

        @Override
        public CompletableFuture<Void> startProcessor() {
            return processor.start();
        }
    }
}
