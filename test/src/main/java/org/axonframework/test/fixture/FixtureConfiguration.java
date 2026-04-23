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

import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.eventhandling.EventSink;

/**
 * Configuration record holding the per-fixture infrastructure components that can be customized by a
 * {@link FixtureCustomizer}.
 * <p>
 * Contains the outermost {@link CommandBus} and {@link EventSink} used for dispatching, and the
 * {@link RecordingComponentsRegistry} used for assertions. A {@code FixtureCustomizer} receives this configuration and
 * returns a (possibly wrapped) version that the fixture will use instead.
 *
 * @param commandBus The outermost {@link CommandBus} from the decorator chain, used for dispatching commands.
 * @param eventSink  The outermost {@link EventSink} from the decorator chain, used for publishing events.
 * @param recordings The {@link RecordingComponentsRegistry} holding the innermost recording decorators for assertions.
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
public record FixtureConfiguration(
        CommandBus commandBus,
        EventSink eventSink,
        RecordingComponentsRegistry recordings
) {

}
