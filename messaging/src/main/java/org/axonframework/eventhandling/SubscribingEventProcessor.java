/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * Event processor implementation that {@link EventBus#subscribe(Consumer) subscribes} to the {@link EventBus} for
 * events. Events published on the event bus are supplied to this processor in the publishing thread.
 * <p>
 * Depending on the given {@link EventProcessingStrategy} the events are processed directly (in the publishing thread)
 * or asynchronously.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class SubscribingEventProcessor implements EventProcessor, DescribableComponent {

    private final String name;
    private final SubscribableMessageSource<? extends EventMessage<?>> messageSource;
    private final UnitOfWorkFactory unitOfWorkFactory;
    private final ProcessorEventHandlingComponents eventHandlingComponents;
    private final EventProcessingStrategy processingStrategy;
    private final ErrorHandler errorHandler;

    private volatile Registration eventBusRegistration;

    public SubscribingEventProcessor(
            @Nonnull String name,
            @Nonnull List<EventHandlingComponent> eventHandlingComponents,
            @Nonnull UnaryOperator<SubscribingEventProcessorConfiguration> customization
    ) {
        this(
                Objects.requireNonNull(name, "Name may not be null"),
                Objects.requireNonNull(eventHandlingComponents, "EventHandlingComponents may not be null"),
                Objects.requireNonNull(customization, "Customization may not be null")
                       .apply(new SubscribingEventProcessorConfiguration())
        );
    }

    public SubscribingEventProcessor(
            @Nonnull String name,
            @Nonnull List<EventHandlingComponent> eventHandlingComponents,
            @Nonnull SubscribingEventProcessorConfiguration configuration
    ) {
        this.name = Objects.requireNonNull(name, "Name may not be null");
        assertThat(name, n -> Objects.nonNull(n) && !n.isEmpty(), "Event Processor name may not be null or empty");
        Objects.requireNonNull(configuration, "SubscribingEventProcessorConfiguration may not be null");
        configuration.validate();
        this.messageSource = configuration.messageSource();
        this.unitOfWorkFactory = configuration.unitOfWorkFactory();
        this.eventHandlingComponents = new ProcessorEventHandlingComponents(eventHandlingComponents);
        this.processingStrategy = configuration.processingStrategy();
        this.errorHandler = configuration.errorHandler();
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Start this processor. This will register the processor with the {@link EventBus}.
     * <p>
     * Upon start up of an application, this method will be invoked in the
     * {@link Phase#LOCAL_MESSAGE_HANDLER_REGISTRATIONS} phase.
     */
    @Override
    public void start() {
        if (eventBusRegistration != null) {
            // This event processor has already been started
            return;
        }
        eventBusRegistration =
                messageSource.subscribe(eventMessages -> processingStrategy.handle(eventMessages, this::process));
    }

    @Override
    public boolean isRunning() {
        return eventBusRegistration != null;
    }

    @Override
    public boolean isError() {
        // this implementation will never stop because of an error
        return false;
    }

    /**
     * Process the given messages. A Unit of Work must be created for this processing.
     * <p>
     * This implementation creates a Batching unit of work for the given batch of {@code eventMessages}.
     *
     * @param eventMessages The messages to process
     */
    protected void process(List<? extends EventMessage<?>> eventMessages) {
        try {
            var unitOfWork = unitOfWorkFactory.create();
            unitOfWork.onInvocation(processingContext -> processWithErrorHandling(eventMessages,
                                                                                  processingContext).asCompletableFuture());
            FutureUtils.joinAndUnwrap(unitOfWork.execute());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new EventProcessingException("Exception occurred while processing events", e);
        }
    }

    private MessageStream.Empty<Message<Void>> processWithErrorHandling(List<? extends EventMessage<?>> events,
                                                                        ProcessingContext context) {
        return eventHandlingComponents.handle(events, context)
                                      .onErrorContinue(ex -> {
                                          try {
                                              errorHandler.handleError(new ErrorContext(name, ex, events));
                                          } catch (RuntimeException re) {
                                              return MessageStream.failed(re);
                                          } catch (Exception e) {
                                              return MessageStream.failed(new EventProcessingException(
                                                      "Exception occurred while processing events",
                                                      e));
                                          }
                                          return MessageStream.empty().cast();
                                      })
                                      .ignoreEntries()
                                      .cast();
    }

    /**
     * Shut down this processor. This will deregister the processor with the {@link EventBus}.
     * <p>
     * Upon shutdown of an application, this method will be invoked in the
     * {@link Phase#LOCAL_MESSAGE_HANDLER_REGISTRATIONS} phase.
     */
    @Override
    public void shutDown() {
        if (eventBusRegistration != null) {
            eventBusRegistration.cancel();
        }
        eventBusRegistration = null;
    }

    /**
     * Returns the message source from which this processor receives its events
     *
     * @return the MessageSource from which the processor receives its events
     */
    public SubscribableMessageSource<? extends EventMessage<?>> getMessageSource() {
        return messageSource;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("mode", "subscribing");
    }
}
