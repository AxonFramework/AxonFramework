/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.axonserver.connector.processor;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.admin.AdminChannel;
import io.axoniq.axonserver.connector.control.ControlChannel;
import io.axoniq.axonserver.connector.control.ProcessorInstructionHandler;
import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
import io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import static java.util.stream.Collectors.toMap;

/**
 * Service that listens to {@link PlatformOutboundInstruction}s to control {@link EventProcessor}s when for example
 * requested by Axon Server. Will delegate the calls to the {@link AxonServerConnectionManager} for further processing.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class EventProcessorControlService implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessorControlService.class);
    private static final String SUBSCRIBING_EVENT_PROCESSOR_MODE = "Subscribing";
    private static final String UNKNOWN_EVENT_PROCESSOR_MODE = "Unknown";

    protected final AxonServerConnectionManager axonServerConnectionManager;
    protected final EventProcessingConfiguration eventProcessingConfiguration;
    protected final String context;
    protected final Map<String, AxonServerConfiguration.EventProcessorConfiguration.ProcessorSettings> processorConfig;

    /**
     * Initialize a {@link EventProcessorControlService}.
     * <p>
     * This service adds processor instruction handlers to the {@link ControlChannel} of the
     * {@link AxonServerConnectionManager#getDefaultContext() default context}. Doing so ensures operation like the
     * {@link EventProcessor#start() start} and {@link EventProcessor#shutDown() shutdown} can be triggered through Axon
     * Server. Furthermore, it sets the configured
     * {@link AxonServerConfiguration.EventProcessorConfiguration.LoadBalancingStrategy load balancing strategies}
     * through the {@link AdminChannel}  of the default context.
     *
     * @param axonServerConnectionManager  A {@link AxonServerConnectionManager} from which to retrieve the
     *                                     {@link ControlChannel} and {@link AdminChannel}.
     * @param eventProcessingConfiguration The {@link EventProcessor} configuration of this application, used to
     *                                     retrieve the registered event processors from.
     * @param axonServerConfiguration      The {@link AxonServerConfiguration} used to retrieve the
     *                                     {@link AxonServerConnectionManager#getDefaultContext() default context}
     *                                     from.
     */
    public EventProcessorControlService(AxonServerConnectionManager axonServerConnectionManager,
                                        EventProcessingConfiguration eventProcessingConfiguration,
                                        AxonServerConfiguration axonServerConfiguration) {
        this(axonServerConnectionManager,
             eventProcessingConfiguration,
             axonServerConfiguration.getContext(),
             axonServerConfiguration.getEventProcessorConfiguration().getProcessors());
    }

    /**
     * Initialize a {@link EventProcessorControlService}.
     * <p>
     * This service adds processor instruction handlers to the {@link ControlChannel} of the given {@code context}.
     * Doing so ensures operation like the {@link EventProcessor#start() start} and
     * {@link EventProcessor#shutDown() shutdown} can be triggered through Axon Server. Furthermore, it sets the
     * configured
     * {@link AxonServerConfiguration.EventProcessorConfiguration.LoadBalancingStrategy load balancing strategies}
     * through the {@link AdminChannel}  of the {@code context}.
     *
     * @param axonServerConnectionManager  A {@link AxonServerConnectionManager} from which to retrieve the
     *                                     {@link ControlChannel} and {@link AdminChannel}.
     * @param eventProcessingConfiguration The {@link EventProcessor} configuration of this application, used to
     *                                     retrieve the registered event processors from.
     * @param context                      The context of this application instance to retrieve the
     *                                     {@link ControlChannel} and {@link AdminChannel} for.
     * @deprecated Please use
     * {@link #EventProcessorControlService(AxonServerConnectionManager, EventProcessingConfiguration, String, Map)} to
     * ensure processor settings are provided.
     */
    @Deprecated
    public EventProcessorControlService(AxonServerConnectionManager axonServerConnectionManager,
                                        EventProcessingConfiguration eventProcessingConfiguration,
                                        String context) {
        this(axonServerConnectionManager, eventProcessingConfiguration, context, Collections.emptyMap());
    }

    /**
     * Initialize a {@link EventProcessorControlService}.
     * <p>
     * This service adds processor instruction handlers to the {@link ControlChannel} of the given {@code context}.
     * Doing so ensures operation like the {@link EventProcessor#start() start} and
     * {@link EventProcessor#shutDown() shutdown} can be triggered through Axon Server. Furthermore, it sets the
     * configured
     * {@link AxonServerConfiguration.EventProcessorConfiguration.LoadBalancingStrategy load balancing strategies}
     * through the {@link AdminChannel}  of the {@code context}.
     *
     * @param axonServerConnectionManager  A {@link AxonServerConnectionManager} from which to retrieve the
     *                                     {@link ControlChannel} and {@link AdminChannel}.
     * @param eventProcessingConfiguration The {@link EventProcessor} configuration of this application, used to
     *                                     retrieve the registered event processors from.
     * @param context                      The context of this application instance to retrieve the
     *                                     {@link ControlChannel} and {@link AdminChannel} for.
     * @param processorConfig              The processor configuration from the {@link AxonServerConfiguration}, used to
     *                                     (for example) retrieve the load balancing strategies from.
     */
    public EventProcessorControlService(AxonServerConnectionManager axonServerConnectionManager,
                                        EventProcessingConfiguration eventProcessingConfiguration,
                                        String context,
                                        Map<String, AxonServerConfiguration.EventProcessorConfiguration.ProcessorSettings> processorConfig) {
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.eventProcessingConfiguration = eventProcessingConfiguration;
        this.context = context;
        this.processorConfig = processorConfig;
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onStart(Phase.INSTRUCTION_COMPONENTS, this::start);
    }

    /**
     * Registers {@link EventProcessor} instruction handlers to the {@link ControlChannel} for the configured
     * {@code context} and set the load balancing strategies through the {@link AdminChannel} for the configured
     * {@code context}.
     * <p>
     * The registration is performed in {@link Phase#INBOUND_EVENT_CONNECTORS} phase, to ensure the event processors
     * this service provides control over have been started.
     */
    @SuppressWarnings("Duplicates")
    public void start() {
        if (axonServerConnectionManager == null || eventProcessingConfiguration == null) {
            return;
        }

        Map<String, EventProcessor> eventProcessors = eventProcessingConfiguration.eventProcessors();
        AxonServerConnection connection = axonServerConnectionManager.getConnection(context);

        registerInstructionHandlers(connection, eventProcessors);
        setLoadBalancingStrategies(connection, eventProcessors.keySet());
    }

    private void setLoadBalancingStrategies(AxonServerConnection connection, Set<String> processorNames) {
        AdminChannel adminChannel = connection.adminChannel();

        Map<String, AxonServerConfiguration.EventProcessorConfiguration.LoadBalancingStrategy> strategiesPerProcessor =
                processorConfig.entrySet()
                               .stream()
                               .filter(entry -> {
                                   if (!processorNames.contains(entry.getKey())) {
                                       logger.info("Event Processor [{}] is not a registered. "
                                                           + "Please check the name or register the Event Processor",
                                                   entry.getKey());
                                       return false;
                                   }
                                   return true;
                               })
                               .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().getLoadBalancingStrategy()));

        strategiesPerProcessor.forEach((processorName, strategy) -> {
            Optional<String> tokenStoreIdentifier = tokenStoreIdentifierFor(processorName);
            if (!tokenStoreIdentifier.isPresent()) {
                logger.warn("Cannot find token store identifier for processor [{}]. "
                                    + "Load balancing cannot be configured without this identifier.", processorName);
                return;
            }

            adminChannel.setAutoLoadBalanceStrategy(processorName, tokenStoreIdentifier.get(), strategy.describe())
                        .whenComplete((r, e) -> {
                            if (e == null) {
                                logger.debug("Successfully requested to automatically balance processor [{}]"
                                                     + " with strategy [{}].", processorName, strategy.describe());
                                return;
                            }
                            logger.warn("Requesting to automatically balance processor [{}] with strategy [{}] failed.",
                                        processorName, strategy.describe(), e);
                        });
        });
    }

    private Optional<String> tokenStoreIdentifierFor(String processorName) {
        return eventProcessingConfiguration.tokenStore(processorName)
                                           .retrieveStorageIdentifier();
    }

    private void registerInstructionHandlers(AxonServerConnection connection,
                                             Map<String, EventProcessor> eventProcessors) {
        ControlChannel controlChannel = connection.controlChannel();
        eventProcessors.forEach((name, processor) -> controlChannel.registerEventProcessor(
                name, infoSupplier(processor), new AxonProcessorInstructionHandler(processor, name)
        ));
    }

    private Supplier<EventProcessorInfo> infoSupplier(EventProcessor processor) {
        if (processor instanceof StreamingEventProcessor) {
            return () -> StreamingEventProcessorInfoMessage.describe((StreamingEventProcessor) processor);
        } else if (processor instanceof SubscribingEventProcessor) {
            return () -> subscribingProcessorInfo(processor);
        } else {
            return () -> unknownProcessorTypeInfo(processor);
        }
    }

    private EventProcessorInfo subscribingProcessorInfo(EventProcessor eventProcessor) {
        return EventProcessorInfo.newBuilder()
                                 .setProcessorName(eventProcessor.getName())
                                 .setMode(SUBSCRIBING_EVENT_PROCESSOR_MODE)
                                 .setIsStreamingProcessor(false)
                                 .build();
    }

    private EventProcessorInfo unknownProcessorTypeInfo(EventProcessor eventProcessor) {
        return EventProcessorInfo.newBuilder()
                                 .setProcessorName(eventProcessor.getName())
                                 .setMode(UNKNOWN_EVENT_PROCESSOR_MODE)
                                 .setIsStreamingProcessor(false)
                                 .build();
    }

    protected static class AxonProcessorInstructionHandler implements ProcessorInstructionHandler {

        private final EventProcessor processor;
        private final String name;

        public AxonProcessorInstructionHandler(EventProcessor processor, String name) {
            this.processor = processor;
            this.name = name;
        }

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
                return exceptionallyCompletedFuture(e);
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
                return exceptionallyCompletedFuture(e);
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
                return exceptionallyCompletedFuture(e);
            }
        }

        @Override
        public CompletableFuture<Void> pauseProcessor() {
            try {
                processor.shutDown();
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                return exceptionallyCompletedFuture(e);
            }
        }

        @Override
        public CompletableFuture<Void> startProcessor() {
            try {
                processor.start();
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                return exceptionallyCompletedFuture(e);
            }
        }

        private <T> CompletableFuture<T> exceptionallyCompletedFuture(Exception e) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}
