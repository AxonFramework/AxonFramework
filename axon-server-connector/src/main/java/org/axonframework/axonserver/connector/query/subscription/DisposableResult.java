/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.axonserver.connector.query.subscription;

import org.axonframework.common.Registration;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A decorator of the {@link SubscriptionQueryResult} which adds the {@link #cancel()} operation.
 *
 * @param <I> a generic specifying the type of the initial result of the {@link SubscriptionQueryResult}
 * @param <U> a generic specifying the type of the subsequent updates of the {@link SubscriptionQueryResult}
 * @author Sara Pellegrini
 * @since 4.0
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class DisposableResult<I, U> implements SubscriptionQueryResult<I, U> {

    private final SubscriptionQueryResult<I, U> delegate;
    private final Registration registration;

    /**
     * Instantiate a {@link DisposableResult} wrapping the {@code delegate}, opening up the capability to {@link
     * #cancel()} it.
     *
     * @param delegate     a {@link SubscriptionQueryResult} which will be wrapped
     * @param registration a {@link Registration} paired with the {@code delegate}
     */
    public DisposableResult(SubscriptionQueryResult<I, U> delegate, Registration registration) {
        this.delegate = delegate;
        this.registration = registration;
    }

    @Override
    public Mono<I> initialResult() {
        return delegate.initialResult();
    }

    @Override
    public Flux<U> updates() {
        return delegate.updates();
    }

    @Override
    public boolean cancel() {
        return registration.cancel() && delegate.cancel();
    }
}
