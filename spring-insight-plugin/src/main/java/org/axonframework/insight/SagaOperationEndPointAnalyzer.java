package org.axonframework.insight;

import com.springsource.insight.intercept.endpoint.EndPointAnalysis;
import com.springsource.insight.intercept.endpoint.EndPointAnalyzer;
import com.springsource.insight.intercept.endpoint.EndPointName;
import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.trace.Frame;
import com.springsource.insight.intercept.trace.FrameUtil;
import com.springsource.insight.intercept.trace.Trace;

import static com.springsource.insight.intercept.operation.OperationFields.*;

/**
 * Analyzer for Axon Saga operations.
 * 
 * @author Joris Kuipers
 * @since 2.0
 */
public class SagaOperationEndPointAnalyzer implements EndPointAnalyzer {

    public EndPointAnalysis locateEndPoint(Trace trace) {
        Frame handlerFrame = trace.getFirstFrameOfType(AxonOperationType.SAGA);
        if (handlerFrame == null) return null;
        
        Operation handlerOp = handlerFrame.getOperation();
        if (handlerOp == null) return null;
        
        EndPointName endPointName = EndPointName.valueOf(
            handlerOp.get(CLASS_NAME) + "#" + handlerOp.get(METHOD_NAME));
        
        return new EndPointAnalysis(handlerFrame.getRange(),
                                     endPointName,
                                     handlerOp.getLabel(),
                                     "SAGA: " + handlerOp.get(SHORT_CLASS_NAME),
                                     FrameUtil.getDepth(handlerFrame));
    }

}
