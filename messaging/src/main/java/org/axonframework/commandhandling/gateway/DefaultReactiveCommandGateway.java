/*
 * Copyright (c) 2010-2020. Axon Framework
 *
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

package org.axonframework.commandhandling.gateway;

import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link ReactiveCommandGateway}.
 *
 * @author Milan Savic
 * @since 4.4
 */
public class DefaultReactiveCommandGateway implements ReactiveCommandGateway {

    private final CommandGateway delegate;

    /**
     * Creates an instance of {@link DefaultReactiveCommandGateway}.
     *
     * @param delegate the delegate {@link CommandGateway} used to perform the actual command dispatching
     */
    public DefaultReactiveCommandGateway(CommandGateway delegate) {
        this.delegate = delegate;
    }

    @Override
    public <R> Mono<R> send(Object command) {
        return Mono.fromFuture(delegate.send(command));
    }
}
