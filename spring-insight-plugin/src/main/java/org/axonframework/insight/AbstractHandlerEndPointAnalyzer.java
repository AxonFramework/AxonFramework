/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.insight;

import com.springsource.insight.intercept.endpoint.EndPointAnalysis;
import com.springsource.insight.intercept.endpoint.EndPointAnalyzer;
import com.springsource.insight.intercept.endpoint.EndPointName;
import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationType;
import com.springsource.insight.intercept.trace.Frame;
import com.springsource.insight.intercept.trace.FrameUtil;
import com.springsource.insight.intercept.trace.Trace;

import static com.springsource.insight.intercept.operation.OperationFields.CLASS_NAME;
import static com.springsource.insight.intercept.operation.OperationFields.METHOD_NAME;

/**
 * Common handling flow for Event- and CommandHandlers.
 *
 * @author Joris Kuipers
 * @since 2.0
 */
public abstract class AbstractHandlerEndPointAnalyzer implements EndPointAnalyzer {

    public EndPointAnalysis locateEndPoint(Trace trace) {
        Frame busFrame = trace.getFirstFrameOfType(getBusOperationType());
        if (busFrame == null) {
            return null;
        }

        Frame handlerFrame = trace.getFirstFrameOfType(getHandlerOperationType());
        if (handlerFrame == null || !FrameUtil.frameIsAncestor(busFrame, handlerFrame)) {
            return null;
        }

        Operation handlerOp = handlerFrame.getOperation();
        if (handlerOp == null) {
            return null;
        }

        EndPointName endPointName = EndPointName.valueOf(
                handlerOp.get(CLASS_NAME) + "#" + handlerOp.get(METHOD_NAME));

        return new EndPointAnalysis(busFrame.getRange(), endPointName, handlerOp.getLabel(),
                                    getExample(busFrame.getOperation()), FrameUtil.getDepth(handlerFrame));
    }

    abstract OperationType getBusOperationType();

    abstract OperationType getHandlerOperationType();

    abstract String getExample(Operation operation);
}
