package org.axonframework.messaging;

public interface ResultAwareMessageDispatchInterceptor<T extends Message<?>> {

    void dispatch(T message, ResultHandler resultHandler, DispatchInterceptorChain<? extends T> interceptorChain);

    interface DispatchInterceptorChain<T extends Message<?>> {

        void proceed(T message, ResultHandler resultHandler);

    }

    interface ResultHandler {

        void handleResultMessage(Message<?> message);

        void onComplete();

        void onError(Exception e);
    }
}
