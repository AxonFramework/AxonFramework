package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;

import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;

/**
 * Abstract implementation of the RoutingStrategy interface that uses a policy to prescribe what happens when a routing
 * cannot be resolved.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractRoutingStrategy implements RoutingStrategy {

    private static final String STATIC_ROUTING_KEY = "unresolved";

    private final UnresolvedRoutingKeyPolicy unresolvedRoutingKeyPolicy;
    private final AtomicLong counter = new AtomicLong(0);

    /**
     * Initializes the strategy using given <code>unresolvedRoutingKeyPolicy</code> prescribing what happens when a
     * routing key cannot be resolved.
     *
     * @param unresolvedRoutingKeyPolicy The policy for unresolved routing keys.
     */
    public AbstractRoutingStrategy(UnresolvedRoutingKeyPolicy unresolvedRoutingKeyPolicy) {
        this.unresolvedRoutingKeyPolicy = unresolvedRoutingKeyPolicy;
    }

    @Override
    public String getRoutingKey(CommandMessage<?> command) {
        String routingKey = doResolveRoutingKey(command);
        if (routingKey == null) {
            switch (unresolvedRoutingKeyPolicy) {
                case ERROR:
                    throw new CommandDispatchException(format("The command [%s] does not contain a routing key.",
                                                              command.getPayloadType().getName()));
                case RANDOM_KEY:
                    return Long.toHexString(counter.getAndIncrement());
                case STATIC_KEY:
                    return STATIC_ROUTING_KEY;
            }
        }
        return routingKey;
    }

    /**
     * Resolve the Routing Key for the given <code>command</code>.
     *
     * @param command The command to resolve the routing key for
     * @return the String representing the Routing Key, or <code>null</code> if unresolved.
     */
    protected abstract String doResolveRoutingKey(CommandMessage<?> command);
}
