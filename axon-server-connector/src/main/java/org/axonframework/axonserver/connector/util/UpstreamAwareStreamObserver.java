/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.util;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

/**
 * Convenience implementation of a StreamObserver that provides access to the request stream, which allows cancellation
 * of the call, flow control, etc.
 *
 * @param <ResT> the type of response sent by the server
 * @author Allard Buijze
 * @see ClientCallStreamObserver
 * @since 4.2
 */
public abstract class UpstreamAwareStreamObserver<ResT> implements ClientResponseObserver<Object, ResT> {

    private ClientCallStreamObserver<?> requestStream;

    @Override
    public void beforeStart(ClientCallStreamObserver<Object> requestStream) {
        this.requestStream = requestStream;
    }

    /**
     * Returns the request stream observer which allows interaction with the client stream.
     *
     * @return the request stream observer which allows interaction with the client stream
     */
    public ClientCallStreamObserver<?> getRequestStream() {
        return requestStream;
    }

    /**
     * Completes the request steam related to this stream observer. Ignores exceptions that may occur (for instance if
     * the request stream is already half-closed).
     */
    protected void completeRequestStream() {
        if (requestStream != null) {
            try {
                requestStream.onCompleted();
            } catch (Exception ex) {
                // Ignore exceptions on completing the request stream, may already have been closed
            }
        }
    }
}
