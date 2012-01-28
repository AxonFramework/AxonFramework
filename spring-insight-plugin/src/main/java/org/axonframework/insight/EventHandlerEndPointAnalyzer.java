package org.axonframework.insight;

import static com.springsource.insight.intercept.operation.OperationFields.CLASS_NAME;
import static com.springsource.insight.intercept.operation.OperationFields.METHOD_NAME;

import com.springsource.insight.intercept.endpoint.EndPointAnalysis;
import com.springsource.insight.intercept.endpoint.EndPointAnalyzer;
import com.springsource.insight.intercept.endpoint.EndPointName;
import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationType;
import com.springsource.insight.intercept.trace.Frame;
import com.springsource.insight.intercept.trace.Trace;

public class EventHandlerEndPointAnalyzer implements EndPointAnalyzer {

    private static final OperationType EVENT_HANDLER_OP_TYPE = OperationType.valueOf("event_handler_operation");
    private static final int SCORE = 0;

    public EndPointAnalysis locateEndPoint(Trace trace) {
        Frame eventFrame = trace.getFirstFrameOfType(EVENT_HANDLER_OP_TYPE);
        if (eventFrame == null) return null;
        Operation eventHandlerOp = eventFrame.getOperation();
        EndPointName endPointName = EndPointName.valueOf(
            eventHandlerOp.get(CLASS_NAME) + "#" + eventHandlerOp.get(METHOD_NAME));
        
        Operation rootOperation = trace.getRootFrame().getOperation();

        return new EndPointAnalysis(trace.getRange(),
                                     endPointName,
                                     eventHandlerOp.getLabel(),
                                     rootOperation.getLabel(),
                                     SCORE);
    }

}
