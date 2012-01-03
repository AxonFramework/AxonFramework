package org.axonframework.commandbus.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.annotation.AnnotationCommandTargetResolver;

/**
 * RoutingKeyExtractor that expects an {@link org.axonframework.commandhandling.annotation.TargetAggregateIdentifier}
 * annotation on the command message's payload. Commands are routed based on the identifier of the aggregate that they
 * target. This approach ensures that commands to be processed by the same aggregate are dispatched to the same node in
 * a DistributedCommandBus. See {@link AnnotationCommandTargetResolver} for more details.
 * <p/>
 * This class requires the returned Aggregate Identifiers to implement a proper {@link Object#toString()} method. An
 * inconsistent toString() method may result in different members using different routing keys for the same identifier.
 *
 * @author Allard Buijze
 * @see AnnotationCommandTargetResolver
 * @see DistributedCommandBus
 * @since 2.0
 */
public class AnnotationRoutingKeyExtractor implements RoutingKeyExtractor {

    private final AnnotationCommandTargetResolver targetResolver = new AnnotationCommandTargetResolver();

    @Override
    public String getRoutingKey(CommandMessage<?> command) {
        return targetResolver.resolveTarget(command).getIdentifier().toString();
    }
}
