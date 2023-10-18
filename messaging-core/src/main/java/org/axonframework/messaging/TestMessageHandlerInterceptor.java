package org.axonframework.messaging;

public class TestMessageHandlerInterceptor<M extends Message, R>
        implements MessageHandlerInterceptor<M, R> {

    @Override
    public MessageHandler<M, R> intercept(MessageHandler<M, R> handler) {
        // handler.canHandle
        // handler.attributes
        //



        return context -> {
            M message = context.message();
            // we don't know the payload type
            Object payload = message.payload();
            try {
                return handler.handle(context);
            } catch (Exception e) {
                return (R) "arrrrrrr";
            }
        };
    }
}
