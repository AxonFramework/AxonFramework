package org.axonframework.insight;

import com.springsource.insight.intercept.operation.OperationType;

public class AxonOperationType {
    static final OperationType COMMAND_BUS = OperationType.valueOf("command_bus_dispatch");

    static final OperationType COMMAND_HANDLER = OperationType.valueOf("command_handler_operation");

    static final OperationType EVENT_BUS = OperationType.valueOf("eventbus_publish");

    static final OperationType EVENT_HANDLER = OperationType.valueOf("event_handler_operation");

    static final OperationType SAGA_HANDLER = OperationType.valueOf("saga_handler_operation");
}
