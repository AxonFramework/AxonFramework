package org.axonframework.insight;

import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationType;

public class CommandHandlerEndPointAnalyzer extends AbstractHandlerEndPointAnalyzer {

    @Override
    OperationType getBusOperationType() {
        return AxonOperationType.COMMAND_BUS;
    }

    @Override
    OperationType getHandlerOperationType() {
        return AxonOperationType.COMMAND_HANDLER;
    }

    @Override
    String getExample(Operation operation) {
        return "COMMAND: " + operation.get("commandType");
    }

}
