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

/**
 * Custom {@link Appender} used for validating log statements.
 *
 * @author Steven van Beelen
 */
@Plugin(
        name = "InMemoryAppender",
        category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE
)
public class InMemoryAppender extends AbstractAppender {

    public static final boolean DO_NOT_USE_CURRENT_CONTEXT = false;
    private final List<LogEvent> logEvents;

    /**
     * Constructs a {@link InMemoryAppender} based on the given {@code name} and {@code filter}.
     * <p>
     * Will default the {@link org.apache.logging.log4j.core.Layout} to {@code null}, it sets {@code ignoreExceptions}
     * to {@code false}, and it defines the {@link Property Property array} as {@code null}.
     *
     * @param name   The name of this {@link Appender}.
     * @param filter The filter used by this {@link Appender}.
     */
    protected InMemoryAppender(String name, Filter filter) {
        super(name, filter, null, DO_NOT_USE_CURRENT_CONTEXT, null);
        logEvents = new CopyOnWriteArrayList<>();
    }

    @SuppressWarnings("unused") // Suppressed since used by Log4J.
    @PluginFactory
    public static InMemoryAppender createAppender(@PluginAttribute("name") String name,
                                                  @PluginElement("Filter") Filter filter) {
        return new InMemoryAppender(name, filter);
    }

    @Override
    public void append(LogEvent event) {
        logEvents.add(event);
    }

    /**
     * Return the {@link LogEvent LogEvents} this {@link Appender} has {@link Appender#append(LogEvent) added}.
     *
     * @return The {@link LogEvent LogEvents} this {@link Appender} has {@link Appender#append(LogEvent) added}.
     */
    public List<LogEvent> getLogEvents() {
        return logEvents;
    }

    /**
     * Clear the {@link #getLogEvents() logs} of the {@link InMemoryAppender}.
     * <p>
     * Use this to ensure a test starts with a clean log.
     */
    public static void clearLogs() {
        LoggerContext context = LoggerContext.getContext(DO_NOT_USE_CURRENT_CONTEXT);
        Configuration configuration = context.getConfiguration();
        InMemoryAppender inMemoryAppender = configuration.getAppender("InMemoryAppender");
        inMemoryAppender.getLogEvents().clear();
    }

    /**
     * Return the {@link LogEvent LogEvents} the {@link InMemoryAppender} has
     * {@link Appender#append(LogEvent) appended}.
     *
     * @return The {@link LogEvent LogEvents} the {@link InMemoryAppender} has
     * {@link Appender#append(LogEvent) appended}.
     */
    public static List<LogEvent> logEvents() {
        LoggerContext context = LoggerContext.getContext(DO_NOT_USE_CURRENT_CONTEXT);
        Configuration configuration = context.getConfiguration();
        InMemoryAppender inMemoryAppender = configuration.getAppender("InMemoryAppender");
        return new ArrayList<>(inMemoryAppender.getLogEvents());
    }
}
