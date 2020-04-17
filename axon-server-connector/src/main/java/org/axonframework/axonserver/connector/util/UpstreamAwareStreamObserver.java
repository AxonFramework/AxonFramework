package org.axonframework.axonserver.connector.util;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

/**
 * Convenience implementation of a StreamObserver that provides access to the request stream, which allows
 * cancellation of the call, flow control, etc.
 *
 * @param <ResT> The type of response sent by the server
 * @see ClientCallStreamObserver
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
         if( requestStream != null) {
             try {
                 requestStream.onCompleted();
             } catch (Exception ex) {
                // Ignore exceptions on completing the request stream, may already have been closed
             }
         }
    }
}
