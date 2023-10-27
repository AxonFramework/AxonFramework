/*
 * Copyright (c) 2010-2023. Axon Framework
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
package org.axonframework.queryhandling;

import org.axonframework.common.Registration;

import javax.annotation.Nonnull;

/**
 * Describes a class capable of subscribing to the query bus.
 *
 * @author Marc Gathier
 * @since 3.1
 */
public interface QueryHandlerAdapter {

    /**
     * Subscribes the query handlers of this {@link QueryHandlerAdapter} to the given {@link QueryBus}.
     *
     * @param queryBus the query bus to subscribe
     * @return a {@link Registration} to unsubscribe this {@link QueryHandlerAdapter}
     */
    Registration subscribe(@Nonnull QueryBus queryBus);
}
