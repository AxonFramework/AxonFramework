/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventsourcing.utils;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Recording {@link Appender} used for validating log statements.
 * <p>
 * Users should invoke {@link RecordingAppender#startRecording(Predicate)} at, for example, the start of a test case.
 * When validation is
 * needed the logs can be retrieved through {@link RecordingAppender#stopRecording()}.
 *
 * @author Steven van Beelen
 */
@Plugin(
        name = "RecordingAppender",
        category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE
)
public class RecordingAppender extends AbstractAppender {

    private static final Predicate<LogEvent> RECORDING_OFF = e -> false;

    private final List<LogEvent> logEvents = new CopyOnWriteArrayList<>();

    private volatile Predicate<LogEvent> recordingFilter;

    /**
     * Constructs a {@link RecordingAppender} based on the given {@code name} and {@code filter}.
     * <p>
     * Will default the {@link org.apache.logging.log4j.core.Layout} to {@code null}, it sets {@code ignoreExceptions}
     * to {@code false}, and it defines the {@link Property Property array} as {@code null}.
     *
     * @param name   The name of this {@link Appender}.
     * @param filter The filter used by this {@link Appender}.
     */
    protected RecordingAppender(String name, Filter filter) {
        super(name, filter, null, false, null);
        recordingFilter = RECORDING_OFF;
    }

    @SuppressWarnings("unused") // Suppressed since used by Log4J.
    @PluginFactory
    public static RecordingAppender createAppender(@PluginAttribute("name") String name,
                                                   @PluginElement("Filter") Filter filter) {
        return new RecordingAppender(name, filter);
    }

    /**
     * Retrieves the RecordingAppender from the log4j configuration, if present.
     * <p>
     * Note that the appender should be named {@code RECORD}.
     *
     * @return The RecordingAppender in the log4j configuration, or {@code null} if not present.
     */
    public static RecordingAppender getInstance() {
        LoggerContext context = LoggerContext.getContext(false);
        Configuration configuration = context.getConfiguration();
        return configuration.getAppender("RECORD");
    }

    @Override
    public void append(LogEvent event) {
        if (recordingFilter.test(event)) {
            logEvents.add(event.toImmutable());
        }
    }

    /**
     * Start recording {@link LogEvent LogEvents} by this {@link Appender}.
     */
    public void startRecording(Predicate<LogEvent> filter) {
        if (recordingFilter != RECORDING_OFF) {
            throw new IllegalStateException("Recording already started");
        }
        logEvents.clear();
        this.recordingFilter = filter;
    }

    /**
     * Stop the recording by the {@link RecordingAppender}.
     * <p>
     * Returns the {@link LogEvent LogEvents} recorded through {@link Appender#append(LogEvent) appending}.
     *
     * @return The {@link LogEvent LogEvents} the {@link RecordingAppender} has
     * {@link Appender#append(LogEvent) appended}.
     */
    public List<LogEvent> stopRecording() {
        recordingFilter = RECORDING_OFF;
        List<LogEvent> logs = new ArrayList<>(logEvents);
        logEvents.clear();
        return logs;
    }

}
