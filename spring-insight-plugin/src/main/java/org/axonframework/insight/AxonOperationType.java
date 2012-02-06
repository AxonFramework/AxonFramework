package org.axonframework.insight;

import com.springsource.insight.intercept.operation.OperationType;

/**
 * {@link OperationType} constants for the defined Axon operations.
 *
 * @author Joris Kuipers
 * @since 2.0
 */
public class AxonOperationType {

    static final OperationType COMMAND_BUS = OperationType.valueOf("command_bus_dispatch");

    static final OperationType COMMAND_HANDLER = OperationType.valueOf("command_handler_operation");

    static final OperationType EVENT_BUS = OperationType.valueOf("event_bus_publish");

    static final OperationType EVENT_HANDLER = OperationType.valueOf("event_handler_operation");

    static final OperationType SAGA = OperationType.valueOf("saga_operation");
}
