package org.axonframework.insight;

import static com.springsource.insight.intercept.operation.OperationFields.CLASS_NAME;
import static com.springsource.insight.intercept.operation.OperationFields.METHOD_NAME;

import com.springsource.insight.intercept.endpoint.EndPointAnalysis;
import com.springsource.insight.intercept.endpoint.EndPointAnalyzer;
import com.springsource.insight.intercept.endpoint.EndPointName;
import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationType;
import com.springsource.insight.intercept.trace.Frame;
import com.springsource.insight.intercept.trace.FrameUtil;
import com.springsource.insight.intercept.trace.Trace;

/**
 * Common handling flow for Event- and CommandHandlers.
 * 
 * @author Joris Kuipers
 *
 */
public abstract class AbstractHandlerEndPointAnalyzer implements EndPointAnalyzer {

    public EndPointAnalysis locateEndPoint(Trace trace) {
        Frame busFrame = trace.getFirstFrameOfType(getBusOperationType());
        if (busFrame == null) return null;
        
        Frame handlerFrame = trace.getFirstFrameOfType(getHandlerOperationType());
        if (handlerFrame == null || !FrameUtil.frameIsAncestor(busFrame, handlerFrame)) {
            return null;
        }
        
        Operation handlerOp = handlerFrame.getOperation();
        if (handlerOp == null) return null;
        
        EndPointName endPointName = EndPointName.valueOf(
            handlerOp.get(CLASS_NAME) + "#" + handlerOp.get(METHOD_NAME));
        
        return new EndPointAnalysis(busFrame.getRange(),
                                     endPointName,
                                     handlerOp.getLabel(),
                                     getExample(busFrame.getOperation()),
                                     FrameUtil.getDepth(handlerFrame));
    }

    abstract OperationType getBusOperationType();
    
    abstract OperationType getHandlerOperationType();
    
    abstract String getExample(Operation operation);

}
