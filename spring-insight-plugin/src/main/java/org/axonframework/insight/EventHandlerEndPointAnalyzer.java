package org.axonframework.insight;

import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationType;

/**
 * Concrete analyzer for Axon event handler operations.
 * 
 * @author Joris Kuipers
 *
 */
public class EventHandlerEndPointAnalyzer extends AbstractHandlerEndPointAnalyzer {

    @Override
    OperationType getBusOperationType() {
        return AxonOperationType.EVENT_BUS;
    }

    @Override
    OperationType getHandlerOperationType() {
        return AxonOperationType.EVENT_HANDLER;
    }

    @Override
    String getExample(Operation operation) {
        return "EVENT: " + operation.get("eventType");
    }

}
