/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.auditing;

import org.axonframework.commandhandling.CommandMessage;

import java.util.Collections;
import java.util.Map;

/**
 * AuditDataProvider implementation that attaches the command identifier to each Event generated as result of that
 * Command.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CorrelationAuditDataProvider implements AuditDataProvider {

    private static final String DEFAULT_KEY = "command-identifier";

    private final String correlationIdKey;

    /**
     * Initializes the CorrelationAuditDataProvider which attaches the Command Identifier to an Event's MetaData using
     * the default key ("{@value #DEFAULT_KEY}").
     */
    public CorrelationAuditDataProvider() {
        this(DEFAULT_KEY);
    }

    /**
     * Initializes the CorrelationAuditDataProvider which attaches the Command Identifier to an Event's MetaData using
     * the given <code>correlationIdKey</code>.
     *
     * @param correlationIdKey the key under which to store the Command Identifier in the resulting Event's MetaData
     */
    public CorrelationAuditDataProvider(String correlationIdKey) {
        this.correlationIdKey = correlationIdKey;
    }

    @Override
    public Map<String, Object> provideAuditDataFor(CommandMessage<?> command) {
        return Collections.singletonMap(correlationIdKey, (Object) command.getIdentifier());
    }
}
