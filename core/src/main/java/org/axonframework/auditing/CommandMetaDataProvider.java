package org.axonframework.auditing;

import org.axonframework.commandhandling.CommandMessage;

import java.util.Map;

/**
 * AuditDataProvider implementation that attaches a Command's MetaData to each event generated as result of that
 * command.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandMetaDataProvider implements AuditDataProvider {

    @Override
    public Map<String, Object> provideAuditDataFor(CommandMessage<?> command) {
        return command.getMetaData();
    }
}
