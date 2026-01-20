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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.test.util.MessageMonitorReport.Report.Success;
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
         * Retrieves the message associated with the report.
         *
         * @return the reported message
         */
        Message message();

        /**
         * Represents a successful report indicating that a message has been processed successfully. This class is an
         * immutable record that provides details about the associated message.
         *
         * @param message The reported message.
         * @see MessageMonitor.MonitorCallback#reportSuccess()
         */
        record Success(Message message) implements Report {

        }

        /**
         * Represents a failure report indicating an error occurred during the processing of a message. This record
         * provides details about the associated message and the underlying cause of the failure.
         *
         * @param message The message associated with the failure.
         * @param cause   The exception that caused the failure.
         * @see MessageMonitor.MonitorCallback#reportFailure(Throwable)
         */
        record Failure(Message message, Throwable cause) implements Report {

        }

        /**
         * Represents an ignored report, indicating that the processing of a message was bypassed or not handled. This
         * record provides information about the associated message.
         *
         * @param message The message associated with the ignored report.
         * @see MessageMonitor.MonitorCallback#reportIgnored()
         */
        record Ignored(Message message) implements Report {

        }
    }

    /**
     * Creates a new instance of {@link Success} with the specified message.
     *
     * @param message The message associated with the success report; must not be null.
     * @return A {@link Success} instance with the provided message.
     */
    public static Report.Success success(Message message) {
        return new Report.Success(message);
    }

    /**
     * Creates a new instance of {@link Report.Ignored} with the specified message.
     *
     * @param message The message associated with the ignored report; must not be null.
     * @return An {@link Report.Ignored} instance with the specified message.
     */
    public static Report.Ignored ignored(Message message) {
        return new Report.Ignored(message);
    }

    /**
     * Creates a new instance of {@link Report.Failure} with the specified message and cause.
     *
     * @param message The message associated with the failure report; must not be null.
     * @param cause   The exception that caused the failure; must not be null.
     * @return A {@link Report.Failure} instance with the specified message and cause.
     */
    public static Report.Failure failure(Message message, Throwable cause) {
        return new Report.Failure(message, cause);
    }

    private final List<Report> delegate = new CopyOnWriteArrayList<>();

    @Override
    public void add(int index, @Nonnull Report report) {
        delegate.add(index, report);
    }

    @Override
    public Report get(int index) {
        return delegate.get(index);
    }

    /**
     * Retrieves a list of {@link Report.Success} elements filtered from the collection of reports.
     *
     * @return a list of {@link Report.Success} instances representing the successful reports
     */
    public List<Report.Success> successReports() {
        return delegate.stream()
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
    public List<Report.Failure> failureReports() {
        return delegate.stream()
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
    public List<Report.Ignored> ignoredReports() {
        return delegate.stream()
                       .filter(Report.Ignored.class::isInstance)
                       .map(Report.Ignored.class::cast)
                       .toList();
    }

    @Override
    public @NotNull Iterator<Report> iterator() {
        return Collections.unmodifiableList(delegate).iterator();
    }

    @Override
    public int size() {
        return delegate.size();
    }
}
