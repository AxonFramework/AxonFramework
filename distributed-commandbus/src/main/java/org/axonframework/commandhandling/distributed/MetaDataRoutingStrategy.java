package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;

/**
 * RoutingStrategy implementation that uses the value in the MetaData of a CommandMessage assigned to a given key. The
 * value's <code>toString()</code> is used to convert the MetaData value to a String.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MetaDataRoutingStrategy implements RoutingStrategy {

    private final String metaDataKey;

    /**
     * Initializes the MetaDataRoutingStrategy where the given <code>metaDataKey</code> is used to get the Meta Data
     * value.
     *
     * @param metaDataKey The key on which the value is retrieved from the MetaData.
     */
    public MetaDataRoutingStrategy(String metaDataKey) {
        this.metaDataKey = metaDataKey;
    }

    @Override
    public String getRoutingKey(CommandMessage<?> command) {
        Object value = command.getMetaData().get(metaDataKey);
        return value == null ? null : value.toString();
    }
}
