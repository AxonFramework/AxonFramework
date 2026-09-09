/*
 * Copyright (c) 2010-2026. Axon Framework
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

import org.jspecify.annotations.Nullable;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.eventhandling.EventMessage;

/**
 * Implementation of the {@link AxonTestThenMessage then-message-phase} for
 * {@link EventMessage EventMessages} of the {@link AxonTestFixture}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AxonTestThenNothing
        extends AxonTestThenMessage<AxonTestPhase.Then.Nothing>
        implements AxonTestPhase.Then.Nothing {

    /**
     * Constructs an {@code AxonTestThenNothing} for the given parameters.
     *
     * @param configuration   The configuration which this test fixture phase is based on.
     * @param customization   Collection of customizations made for this test fixture.
     * @param recordings      The registry holding recording components for assertions.
     * @param actualException The exception thrown during the when-phase, potentially {@code null}.
     */
    public AxonTestThenNothing(
            AxonConfiguration configuration,
            AxonTestFixture.Customization customization,
            RecordingComponentsRegistry recordings,
            @Nullable Throwable actualException
    ) {
        super(configuration, customization, recordings, actualException);
    }
}
