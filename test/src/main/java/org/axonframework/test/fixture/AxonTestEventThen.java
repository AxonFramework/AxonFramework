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

import org.axonframework.configuration.NewConfiguration;

class AxonTestEventThen
        extends AxonTestMessageThen<AxonTestPhase.Then.EventThen>
        implements AxonTestPhase.Then.EventThen {

    private final NewConfiguration configuration;
    private final AxonTestFixture.Customization customization;

    public AxonTestEventThen(
            NewConfiguration configuration,
            AxonTestFixture.Customization customization,
            RecordingCommandBus commandBus,
            RecordingEventSink eventSink,
            Throwable lastException
    ) {
        super(configuration, customization, commandBus, eventSink, lastException);
        this.configuration = configuration;
        this.customization = customization;
    }

    @Override
    public AxonTestPhase.Setup and() {
        return AxonTestFixture.with(configuration, c -> customization);
    }

    @Override
    public AxonTestEventThen self() {
        return this;
    }
}
