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

package org.axonframework.test.util;


import org.apache.commons.lang3.tuple.Pair;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingMessageMonitorTest {

    @Test
    void callsOnReportHooksWhenIngesting() {
        var success = new AtomicReference<Message>();
        var failure = new AtomicReference<Pair<Message, Throwable>>();
        var ignore = new AtomicReference<Message>();

        var successMessage = new GenericEventMessage(new MessageType("event"), "Success");
        var failureMessage = new GenericEventMessage(new MessageType("event"), "Failure");
        var exception = new RuntimeException("Test");
        var ignoreMessage = new GenericEventMessage(new MessageType("event"), "Ignore");

        var monitor = new RecordingMessageMonitor() {
            @Override
            protected void onReportSuccess(@NotNull Message message) {
                success.set(message);
            }

            @Override
            protected void onReportFailure(@NotNull Message message, @NotNull Throwable cause) {
                failure.set(Pair.of(message, cause));
            }

            @Override
            protected void onReportIgnored(@NotNull Message message) {
                ignore.set(message);
            }
        };

        monitor.onMessageIngested(successMessage).reportSuccess();
        monitor.onMessageIngested(failureMessage).reportFailure(exception);
        monitor.onMessageIngested(ignoreMessage).reportIgnored();

        assertThat(monitor.report().successReports()).hasSize(1);
        assertThat(monitor.report().failureReports()).hasSize(1);
        assertThat(monitor.report().ignoredReports()).hasSize(1);

        assertThat(success).hasValue(successMessage);
        assertThat(ignore).hasValue(ignoreMessage);
        assertThat(failure).hasValue(Pair.of(failureMessage, exception));
    }
}
