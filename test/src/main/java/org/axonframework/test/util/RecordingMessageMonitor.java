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

package org.axonframework.test.util;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.jetbrains.annotations.NotNull;

/**
 * A message monitor implementation that records the processing results of messages in the provided
 * {@link MessageMonitorReport} instances. It supports logging the processing outcomes if logging is enabled. This
 * monitor tracks whether messages were successfully processed, failed, or ignored and stores this information in the
 * provided reports.
 * <p>
 * The class provides two constructors: one allows configuring a single report instance while enabling logging, and the
 * other accepts a list of reports, defaulting logging to disabled.
 * <p>
 * The monitor provides functionality to handle messages as they are ingested and uses a callback to record the
 * processing result for each message.
 * <p/>
 * For further inspection, for example logging, you can overwrite the {@link #onReportSuccess(Message)}, {@link #onReportFailure(Message, Throwable)}
 * and {@link #onReportIgnored(Message)} methods.
 */
public class RecordingMessageMonitor implements MessageMonitor<Message> {

    protected final MessageMonitorReport report;

    /**
     * Constructs a RecordingMessageMonitor that records message processing outcomes in a new {@link MessageMonitorReport}.
     *
     * @see #RecordingMessageMonitor(MessageMonitorReport)
     */
    public RecordingMessageMonitor() {
        this(new MessageMonitorReport());
    }

    /**
     * Constructs a RecordingMessageMonitor that records message processing outcomes in the provided
     * {@link MessageMonitorReport} and configures optional logging.
     *
     * @param report The {@link MessageMonitorReport} instance to record message processing results.
     */
    public RecordingMessageMonitor(@Nonnull MessageMonitorReport report) {
        this.report = report;
    }

    @Override
    public final MonitorCallback onMessageIngested(@NotNull Message message) {

        return new MonitorCallback() {
            @Override
            public void reportSuccess() {
                onReportSuccess(message);
                report.add(MessageMonitorReport.success(message));
            }

            @Override
            public void reportFailure(Throwable cause) {
                onReportFailure(message, cause);
                report.add(MessageMonitorReport.failure(message, cause));
            }

            @Override
            public void reportIgnored() {
                onReportIgnored(message);
                report.add(MessageMonitorReport.ignored(message));
            }
        };
    }

    /**
     * @return Returns the report that stores the results of messages processed by this monitor.
     */
    public MessageMonitorReport report() {
        return report;
    }

    /**
     * Hook for subclasses to perform additional actions when a message is reported as successful.
     *
     * @param message The message that was reported as successful.
     */
    protected void onReportSuccess(@Nonnull Message message) {
        // noop
    }

    /**
     * Hook for subclasses to perform additional actions when a message is reported as failed.
     *
     * @param message The message that was reported as failed.
     * @param cause   The cause of the failure.
     */
    protected void onReportFailure(@Nonnull Message message, @Nonnull Throwable cause) {
        // noop
    }

    /**
     * Hook for subclasses to perform additional actions when a message is reported as ignored.
     *
     * @param message The message that was reported as ignored.
     */
    protected void onReportIgnored(@Nonnull Message message) {
        // noop
    }
}
