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

public class CommandHandlerEndPointAnalyzer implements EndPointAnalyzer {

    private static final OperationType CMD_HANDLER_OP_TYPE = OperationType.valueOf("command_handler_operation");
    private static final int SCORE = 0;

    public EndPointAnalysis locateEndPoint(Trace trace) {
        Frame commandFrame = trace.getFirstFrameOfType(CMD_HANDLER_OP_TYPE);
        if (commandFrame == null) return null;
        Operation commandHandlerOp = commandFrame.getOperation();
        EndPointName endPointName = EndPointName.valueOf(
            commandHandlerOp.get(CLASS_NAME) + "#" + commandHandlerOp.get(METHOD_NAME));
        
        Operation rootOperation = trace.getRootFrame().getOperation();

        return new EndPointAnalysis(trace.getRange(),
                                     endPointName,
                                     commandHandlerOp.getLabel(),
                                     rootOperation.getLabel(),
                                     SCORE);
    }

}
