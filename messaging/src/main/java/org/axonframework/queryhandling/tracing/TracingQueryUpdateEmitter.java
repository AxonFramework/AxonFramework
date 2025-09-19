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

package org.axonframework.queryhandling.tracing;

import jakarta.annotation.Nonnull;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandler;
import org.axonframework.tracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
// TODO 3594 - Introduce monitoring logic here.
public class TracingQueryUpdateEmitter implements QueryUpdateEmitter {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final QueryUpdateEmitter delegate;
    private final QueryUpdateEmitterSpanFactory spanFactory;

    /**
     *
     * @param delegate
     * @param spanFactory
     */
    public TracingQueryUpdateEmitter(@Nonnull QueryUpdateEmitter delegate,
                                     @Nonnull QueryUpdateEmitterSpanFactory spanFactory) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate QueryUpdateEmitter must not be null.");
        this.spanFactory = Objects.requireNonNull(spanFactory, "The QueryUpdateEmitterSpanFactory must not be null.");
    }

    @Override
    public void emit(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                     @Nonnull SubscriptionQueryUpdateMessage update) {
        SubscriptionQueryUpdateMessage updateMessage = spanFactory.propagateContext(update);
        Span span = spanFactory.createUpdateScheduleEmitSpan(updateMessage);
        span.run(() -> {
            Span doEmitSpan = spanFactory.createUpdateEmitSpan(updateMessage);
//            runOnAfterCommitOrNow(doEmitSpan.wrapRunnable(
//                    () -> doEmit(filter, intercept(spanFactory.propagateContext(updateMessage)))));
        });
    }

    @Override
    public void complete(@Nonnull Predicate<SubscriptionQueryMessage> filter) {

    }

    @Override
    public void completeExceptionally(@Nonnull Predicate<SubscriptionQueryMessage> filter, @Nonnull Throwable cause) {

    }

    @Nonnull
    @Override
    public UpdateHandler subscribe(@Nonnull SubscriptionQueryMessage query,
                                   int updateBufferSize) {
        return null;
    }
}
