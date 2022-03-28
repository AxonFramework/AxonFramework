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

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

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

    /**
     * Initialize a {@link EventProcessorControlService} which adds {@link java.util.function.Consumer}s to the given
     * {@link AxonServerConnectionManager} on {@link PlatformOutboundInstruction}s. Uses the {@link
     * AxonServerConnectionManager#getDefaultContext()} specified in the given {@code axonServerConnectionManager} as
     * the context to dispatch operations in
     *
     * @param axonServerConnectionManager a {@link AxonServerConnectionManager} used to add operations when {@link
     *                                    PlatformOutboundInstruction} have been received
     * @param axonServerConfiguration     the {@link AxonServerConfiguration} used to retrieve the client identifier
     */
    public EventProcessorControlService(AxonServerConnectionManager axonServerConnectionManager,
                                        EventProcessingConfiguration eventProcessingConfiguration,
                                        AxonServerConfiguration axonServerConfiguration) {
        this(axonServerConnectionManager,
             eventProcessingConfiguration,
             axonServerConfiguration.getContext());
    }

    /**
     * Initialize a {@link EventProcessorControlService} which adds {@link java.util.function.Consumer}s to the given
     * {@link AxonServerConnectionManager} on {@link PlatformOutboundInstruction}s. Uses the {@link
     * AxonServerConnectionManager#getDefaultContext()} specified in the given {@code axonServerConnectionManager} as
     * the context to dispatch operations in
     *
     * @param axonServerConnectionManager a {@link AxonServerConnectionManager} used to add operations when {@link
     *                                    PlatformOutboundInstruction} have been received
     * @param context                     the context of this application instance within which outbound instruction
     *                                    handlers should be specified on the given {@code axonServerConnectionManager}
     */
    public EventProcessorControlService(AxonServerConnectionManager axonServerConnectionManager,
                                        EventProcessingConfiguration eventProcessingConfiguration,
                                        String context) {
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.eventProcessingConfiguration = eventProcessingConfiguration;
        this.context = context;
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onStart(Phase.INSTRUCTION_COMPONENTS, this::start);
    }

    /**
     * Add {@link java.util.function.Consumer}s to the {@link AxonServerConnectionManager} for several {@link
     * PlatformOutboundInstruction}s. Will be started in phase {@link Phase#INBOUND_EVENT_CONNECTORS}, to ensure the
     * event processors this service provides control over have been started.
     */
    @SuppressWarnings("Duplicates")
    public void start() {
        if (axonServerConnectionManager != null && eventProcessingConfiguration != null) {
            ControlChannel controlChannel = axonServerConnectionManager.getConnection(context)
                                                                       .controlChannel();
            eventProcessingConfiguration.eventProcessors()
                                        .forEach(
                                                (name, processor) ->
                                                        controlChannel.registerEventProcessor(name,
                                                                                              infoSupplier(processor),
                                                                                              new AxonProcessorInstructionHandler(
                                                                                                      processor,
                                                                                                      name)));
        }
    }

    public Supplier<EventProcessorInfo> infoSupplier(EventProcessor processor) {
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
