/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.queryhandling;

/**
 * Abstraction interface to bridge old {@code FluxSink} and {@link reactor.core.publisher.Sinks.Many} API with a common
 * API.
 *
 * @author Stefan Dragisic
 * @since 4.5
 */
public interface SinkWrapper<T> {

    /**
     * Wrapper around Sink complete().
     */
    void complete();

    /**
     * Wrapper around Sink next(Object).
     *
     * @param value to be passed to the delegate sink
     */
    void next(T value);

    /**
     * Wrapper around Sink error(Throwable).
     *
     * @param t to be passed to the delegate sink
     */
    void error(Throwable t);
}
