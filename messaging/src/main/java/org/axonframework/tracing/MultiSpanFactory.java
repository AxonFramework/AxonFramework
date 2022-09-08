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

package org.axonframework.tracing;

import org.axonframework.messaging.Message;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Implementation of a {@link SpanFactory} that can delegates calls to multiple other factories. This can be useful if
 * you want the {@link LoggingSpanFactory} to be used in conjunction with another.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class MultiSpanFactory implements SpanFactory {

    private final List<SpanFactory> spanFactories;

    /**
     * Creates the {@link MultiSpanFactory} with the delegate factory implementations that it should use.
     *
     * @param spanFactories The delegate {@link SpanFactory} implementations it should use.
     */
    public MultiSpanFactory(List<SpanFactory> spanFactories) {
        this.spanFactories = spanFactories;
    }

    @Override
    public Span createRootTrace(Supplier<String> operationNameSupplier) {
        return new MultiSpan(
                spanFactories.stream()
                             .map(sf -> sf.createRootTrace(operationNameSupplier))
                             .collect(Collectors.toList())
        );
    }

    @Override
    public Span createHandlerSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                  boolean isChildTrace, Message<?>... linkedParents) {
        return new MultiSpan(
                spanFactories.stream()
                             .map(sf -> sf.createHandlerSpan(operationNameSupplier,
                                                             parentMessage,
                                                             isChildTrace,
                                                             linkedParents))
                             .collect(Collectors.toList())
        );
    }

    @Override
    public Span createDispatchSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                   Message<?>... linkedSiblings) {
        return new MultiSpan(
                spanFactories.stream()
                             .map(sf -> sf.createDispatchSpan(operationNameSupplier,
                                                              parentMessage,
                                                              linkedSiblings))
                             .collect(Collectors.toList())
        );
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier) {
        return new MultiSpan(
                spanFactories.stream()
                             .map(sf -> sf.createInternalSpan(operationNameSupplier))
                             .collect(Collectors.toList())
        );
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier, Message<?> message) {
        return new MultiSpan(
                spanFactories.stream()
                             .map(sf -> sf.createInternalSpan(operationNameSupplier, message))
                             .collect(Collectors.toList())
        );
    }

    @Override
    public void registerSpanAttributeProvider(SpanAttributesProvider provider) {
        spanFactories.forEach(sf -> sf.registerSpanAttributeProvider(provider));
    }

    @Override
    public <M extends Message<?>> M propagateContext(M message) {
        M adjustedMessage = message;
        for (SpanFactory spanFactory : spanFactories) {
            adjustedMessage = spanFactory.propagateContext(adjustedMessage);
        }

        return adjustedMessage;
    }

    private static class MultiSpan implements Span {

        private final List<Span> spans;

        public MultiSpan(List<Span> spans) {
            this.spans = spans;
        }

        @Override
        public Span start() {
            spans.forEach(Span::start);
            return this;
        }

        @Override
        public void end() {
            spans.forEach(Span::end);
        }

        @Override
        public Span recordException(Throwable t) {
            spans.forEach(s -> s.recordException(t));
            return this;
        }
    }
}
