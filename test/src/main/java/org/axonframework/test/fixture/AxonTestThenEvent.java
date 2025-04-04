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
import org.axonframework.messaging.MessageStream;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.instanceOf;

class AxonTestThenEvent
        extends AxonTestThenMessage<AxonTestPhase.Then.Event>
        implements AxonTestPhase.Then.Event {

    public AxonTestThenEvent(
            NewConfiguration configuration,
            AxonTestFixture.Customization customization,
            RecordingCommandBus commandBus,
            RecordingEventSink eventSink,
            Throwable actualException
    ) {
        super(configuration, customization, commandBus, eventSink, actualException);
    }

    @Override
    public AxonTestPhase.Then.Event success() {
        StringDescription expectedDescription = new StringDescription();
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Event exception(@NotNull Class<? extends Throwable> type) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(MessageStream.Empty.class.getSimpleName(), description);
        }
        return super.exception(type);
    }

    @Override
    public AxonTestPhase.Then.Event exception(@NotNull Class<? extends Throwable> type, @NotNull String message) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(MessageStream.Empty.class.getSimpleName(), description);
        }
        return super.exception(type, message);
    }

    @Override
    public AxonTestPhase.Then.Event exception(@NotNull Consumer<Throwable> consumer) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(MessageStream.Empty.class.getSimpleName(), description);
        }
        return super.exception(consumer);
    }
}
