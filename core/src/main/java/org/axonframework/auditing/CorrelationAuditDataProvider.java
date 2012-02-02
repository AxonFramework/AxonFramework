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
