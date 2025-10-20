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

package org.axonframework.test.utils;

import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public class RecordingMessageMonitor implements MessageMonitor<Message> {

    private final MessageMonitorReport report;
    private final Logger logger;
    private final String context;
    private final boolean isLoggingEnabled;

    /**
     * Constructs a RecordingMessageMonitor that records message processing outcomes in the provided
     * {@link MessageMonitorReport} and configures optional logging.
     *
     * @param report           the {@link MessageMonitorReport} instance to record message processing results
     * @param context          the contextual name used for logging and to differentiate monitoring instances
     * @param isLoggingEnabled a flag indicating if logging should be enabled for message outcomes
     */
    public RecordingMessageMonitor(MessageMonitorReport report, String context, boolean isLoggingEnabled) {
        this.report = report;
        this.context = context;
        this.isLoggingEnabled = isLoggingEnabled;
        this.logger = LoggerFactory.getLogger(context);
    }

    /**
     * Constructs a RecordingMessageMonitor that records message processing outcomes in the provided
     * {@link MessageMonitorReport} and uses the given context for logging and differentiation purposes.
     *
     * @param report  the {@link MessageMonitorReport} instance to record message processing results
     * @param context the contextual name used for logging and to differentiate monitoring instances
     */
    public RecordingMessageMonitor(MessageMonitorReport report, String context) {
        this(report, context, false);
    }

    @Override
    public MonitorCallback onMessageIngested(@NotNull Message message) {

        return new MonitorCallback() {
            @Override
            public void reportSuccess() {
                if (isLoggingEnabled) {
                    logger.info("Report Success {}: message={}", message.getClass().getSimpleName(), message);
                }
                report.add(MessageMonitorReport.success(context, message));
            }

            @Override
            public void reportFailure(Throwable cause) {
                if (isLoggingEnabled) {
                    logger.error("Report Failure {}: message:{}",
                                 message.getClass().getSimpleName(),
                                 message,
                                 cause);
                }
                report.add(MessageMonitorReport.failure(context, message, cause));
            }

            @Override
            public void reportIgnored() {
                if (isLoggingEnabled) {
                    logger.info("Report Ignored {}: message={}", message.getClass().getSimpleName(), message);
                }
                report.add(MessageMonitorReport.ignored(context, message));
            }
        };
    }
}
