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

package org.axonframework.test.fixture;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.DelegatingConfigurer;
import org.axonframework.eventhandling.EventSink;
import org.jetbrains.annotations.NotNull;

public class TestApplicationConfigurer
        extends DelegatingConfigurer<TestApplicationConfigurer>
        implements ApplicationConfigurer<TestApplicationConfigurer> {

    /**
     * Construct a {@code DelegatingConfigurer} using the given {@code delegate} to delegate all operations to.
     *
     * @param delegate The configurer to delegate all operations too.
     */
    public TestApplicationConfigurer(@NotNull ApplicationConfigurer<?> delegate) {
        super(
                delegate.registerDecorator(EventSink.class,
                                           Integer.MAX_VALUE,
                                           (c, name, d) -> new RecordingEventSink(d))
                        .registerDecorator(CommandBus.class,
                                           Integer.MAX_VALUE,
                                           (c, name, d) -> new RecordingCommandBus(d))
        );
    }
}
