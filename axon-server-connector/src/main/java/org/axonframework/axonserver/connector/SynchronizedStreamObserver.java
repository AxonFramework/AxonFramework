package org.axonframework.axonserver.connector;

import io.grpc.stub.StreamObserver;

/**
 * Author: marc
 */
public class SynchronizedStreamObserver<T>
        implements StreamObserver<T> {

    private final StreamObserver<T> delegate;

    public SynchronizedStreamObserver(
            StreamObserver<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onNext(T t) {
        synchronized (delegate) {
            delegate.onNext(t);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        delegate.onError(throwable);
    }

    @Override
    public void onCompleted() {
        delegate.onCompleted();
    }
}
