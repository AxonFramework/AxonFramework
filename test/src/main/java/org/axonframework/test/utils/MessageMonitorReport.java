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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.test.utils.MessageMonitorReport.Report.Success;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MessageMonitorReport is responsible for maintaining a list of reports detailing the processing status of messages.
 * This class encapsulates multiple reports using a thread-safe list implementation.
 */
public class MessageMonitorReport extends AbstractList<MessageMonitorReport.Report> {

    /**
     * Represents a report that provides details about the processing of a message. This interface serves as a base type
     * for various report implementations, each reflecting a specific processing outcome.
     */
    public sealed interface Report {

        /**
         * Returns the context associated with the report. Can be used to identify the origin of reports.
         *
         * @return the context information as a string
         */
        String context();

        /**
         * Retrieves the message associated with the report.
         *
         * @return the reported message
         */
        Message message();

        /**
         * Represents a successful report indicating that a message has been processed successfully. This class is an
         * immutable record that provides details about the context and the associated message.
         *
         * @param context the context information as a string
         * @param message the reported message
         * @see MessageMonitor.MonitorCallback#reportSuccess()
         */
        record Success(String context, Message message) implements Report {

            @Override
            public @Nonnull String toString() {
                return "Success{context=%s, message=%s}".formatted(context, message);
            }
        }

        /**
         * Represents a failure report indicating an error occurred during the processing of a message. This record
         * provides details about the context, the associated message, and the underlying cause of the failure.
         *
         * @param context the context information as a string, typically identifying the origin of the failure
         * @param message the message associated with the failure
         * @param cause   the exception that caused the failure
         * @see MessageMonitor.MonitorCallback#reportFailure(Throwable)
         */
        record Failure(String context, Message message, Throwable cause) implements Report {

            @Override
            public @Nonnull String toString() {
                return "Failure{context=%s, message=%s, caus=%s}".formatted(context, message, cause.getMessage());
            }
        }

        /**
         * Represents an ignored report, indicating that the processing of a message was bypassed or not handled. This
         * record provides information about the context and the associated message but does not reflect success or
         * failure in processing.
         *
         * @param context the context information as a string, typically identifying the origin of the report
         * @param message the message associated with the ignored report
         * @see MessageMonitor.MonitorCallback#reportIgnored()
         */
        record Ignored(String context, Message message) implements Report {

            @Override
            public @Nonnull String toString() {
                return "Ignored{context=%s, message=%s}".formatted(context, message);
            }
        }
    }

    /**
     * Creates a new instance of {@link Success} with a default context of "unknown".
     *
     * @param message the message associated with the success report; must not be null
     * @return a {@link Success} instance with the specified message and a context of "unknown"
     */
    public static Report.Success success(Message message) {
        return success("unknown", message);
    }

    /**
     * Creates a new instance of {@link Success} with the specified context and message.
     *
     * @param context the context information associated with the success report; must not be null
     * @param message the message associated with the success report; must not be null
     * @return a {@link Success} instance with the provided context and message
     */
    public static Report.Success success(String context, Message message) {
        return new Report.Success(context, message);
    }

    /**
     * Creates a new instance of {@link Report.Ignored} with a default context of "unknown".
     *
     * @param message the message associated with the ignored report; must not be null
     * @return a {@link Report.Ignored} instance with the specified message and a context of "unknown"
     */
    public static Report.Ignored ignored(Message message) {
        return ignored("unknown", message);
    }

    /**
     * Creates a new instance of {@link Report.Ignored} with the specified context and message.
     *
     * @param context the context information associated with the ignored report; must not be null
     * @param message the message associated with the ignored report; must not be null
     * @return a {@link Report.Ignored} instance with the provided context and message
     */
    public static Report.Ignored ignored(String context, Message message) {
        return new Report.Ignored(context, message);
    }

    /**
     * Creates a new instance of {@link Report.Failure} with a default context of "unknown".
     *
     * @param message the message associated with the failure report; must not be null
     * @param cause   the exception that caused the failure; must not be null
     * @return a {@link Report.Failure} instance with the specified message, cause, and a context of "unknown"
     */
    public static Report.Failure failure(Message message, Throwable cause) {
        return failure("unknown", message, cause);
    }

    /**
     * Creates a new instance of {@link Report.Failure} with the provided context, message, and cause.
     *
     * @param context the context information associated with the failure report; must not be null
     * @param message the message associated with the failure report; must not be null
     * @param cause   the exception that caused the failure; must not be null
     * @return a {@link Report.Failure} instance with the specified context, message, and cause
     */
    public static Report.Failure failure(String context, Message message, Throwable cause) {
        return new Report.Failure(context, message, cause);
    }

    private final List<Report> reports = new CopyOnWriteArrayList<>();

    @Override
    public void add(int index, @Nonnull Report report) {
        reports.add(index, report);
    }

    @Override
    public Report get(int index) {
        return reports.get(index);
    }

    /**
     * Retrieves a list of {@link Report.Success} elements filtered from the collection of reports.
     *
     * @return a list of {@link Report.Success} instances representing the successful reports
     */
    public List<Report.Success> getSuccess() {
        return reports.stream()
                      .filter(Report.Success.class::isInstance)
                      .map(Report.Success.class::cast)
                      .toList();
    }

    /**
     * Retrieves a list of {@link Report.Failure} elements filtered from the collection of reports. This includes only
     * the reports that are instances of {@link Report.Failure}.
     *
     * @return a list of {@link Report.Failure} instances representing the failure reports
     */
    public List<Report.Failure> getFailures() {
        return reports.stream()
                      .filter(Report.Failure.class::isInstance)
                      .map(Report.Failure.class::cast)
                      .toList();
    }

    /**
     * Retrieves a list of {@link Report.Ignored} elements filtered from the collection of reports. This includes only
     * the reports that are instances of {@link Report.Ignored}.
     *
     * @return a list of {@link Report.Ignored} instances representing the ignored reports
     */
    public List<Report.Ignored> getIgnored() {
        return reports.stream()
                      .filter(Report.Ignored.class::isInstance)
                      .map(Report.Ignored.class::cast)
                      .toList();
    }

    @Override
    public @NotNull Iterator<Report> iterator() {
        return Collections.unmodifiableList(reports).iterator();
    }

    @Override
    public int size() {
        return reports.size();
    }
}
