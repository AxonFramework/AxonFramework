package org.axonframework.axonserver.connector.util;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

/**
 * Convenience implementation of a StreamObserver that provides access to the request stream, which allows
 * cancellation of the call, flow control, etc.
 *
 * @param <ReqT> The type of requests sent by the client
 * @param <ResT> The type of response sent by the server
 * @see ClientCallStreamObserver
 */
public abstract class UpstreamAwareStreamObserver<ReqT, ResT> implements ClientResponseObserver<ReqT, ResT> {

    private ClientCallStreamObserver<ReqT> requestStream;

    @Override
    public void beforeStart(ClientCallStreamObserver<ReqT> requestStream) {
        this.requestStream = requestStream;
    }

    /**
     * Returns the request stream observer which allows interaction with the client stream.
     *
     * @return the request stream observer which allows interaction with the client stream
     */
    public ClientCallStreamObserver<ReqT> getRequestStream() {
        return requestStream;
    }
}
