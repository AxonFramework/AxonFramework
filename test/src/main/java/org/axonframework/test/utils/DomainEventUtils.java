/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.utils;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.test.FixtureExecutionException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.axonframework.util.ReflectionUtils.ensureAccessible;

/**
 * Utility class to set aggregate identifiers and sequence numbers on Domain Events. These settings are generally
 * set by the command handling component. However, when testing Query components in isolation, Events need to be
 * "manually" injected with this information.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public abstract class DomainEventUtils {

    private static Method identifierSetter;
    private static Method sequenceNumberSetter;

    private static NoSuchMethodException initializationError;

    static {
        try {
            identifierSetter = DomainEvent.class.getDeclaredMethod("setAggregateIdentifier", AggregateIdentifier.class);
            sequenceNumberSetter = DomainEvent.class.getDeclaredMethod("setSequenceNumber", long.class);
        } catch (NoSuchMethodException e) {
            initializationError = e;
        }
    }

    private DomainEventUtils() {
    }

    /**
     * Sets the given <code>sequenceNumber</code> on the given <code>event</code>. Note that a sequence number may be
     * set at most once on any given <code>event</code>.
     *
     * @param event          The event to set the sequence number on
     * @param sequenceNumber The sequence number to set on the event
     */
    public static void setSequenceNumber(DomainEvent event, long sequenceNumber) {
        assertInitialized();
        try {
            ensureAccessible(sequenceNumberSetter);
            sequenceNumberSetter.invoke(event, sequenceNumber);
        } catch (IllegalAccessException e) {
            throw new FixtureExecutionException("Cannot inject identifier and sequence number.", e);
        } catch (InvocationTargetException e) {
            throw new FixtureExecutionException("Cannot inject identifier and sequence number.", e);
        }
    }

    /**
     * Sets the given <code>identifier</code> on the given <code>event</code>. Note that an aggregate identifier may be
     * set at most once on any given <code>event</code>.
     *
     * @param event      The event to set the aggregate identifier on
     * @param identifier The aggregate identifier to set on the event
     */
    public static void setAggregateIdentifier(DomainEvent event, AggregateIdentifier identifier) {
        assertInitialized();
        try {
            ensureAccessible(identifierSetter);
            identifierSetter.invoke(event, identifier);
        } catch (IllegalAccessException e) {
            throw new FixtureExecutionException("Cannot inject identifier and sequence number.", e);
        } catch (InvocationTargetException e) {
            throw new FixtureExecutionException("Cannot inject identifier and sequence number.", e);
        }
    }

    private static void assertInitialized() {
        if (initializationError != null) {
            throw new FixtureExecutionException("Cannot inject identifier and sequence number. "
                                                        + "An error occurred while initializing this class",
                                                initializationError);
        }
    }
}
