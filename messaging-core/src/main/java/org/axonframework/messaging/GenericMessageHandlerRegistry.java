package org.axonframework.messaging;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @param <M>
 * @param <R>
 * @param <H>
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class GenericMessageHandlerRegistry<M extends Message, R, H extends MessageHandler<M, R>>
        implements MessageHandlingComponent<M, R, H> {

    private final Map<QualifiedName, H> handlers = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return null;
    }

    @Override
    public Registration registerHandler(Set<QualifiedName> messageTypes, H handler) {
        messageTypes.forEach(name -> handlers.put(name, handler));
        return () -> messageTypes.forEach(handlers::remove);
    }

    @Override
    public Registration registerInterceptor(MessageHandlerInterceptor<M, R> interceptor) {
        return null;
    }

    @Override
    public R handle(MessageHandlingContext<M> context) {
        return null;
    }

    public Optional<H> handler(QualifiedName handlerName) {
        return Optional.ofNullable(handlers.get(handlerName));
    }
}

/*
class OutsideMessageHandlerInterceptor {
    // Reacts on any handler/QualifiedName
}

class EventHandlingComponent {

    @MessageHandlerInterceptor
    public CompletableFuture<Void> intercept(Object event) {

    }

    @EventHandler(for = QualifiedName.anyPayload())
    public CompletableFuture<Void> on(Object event) {

    }

    // generic of anything...
    @EventHandler
    public CompletableFuture<Void> on(EventMessage event) {

    }

    // qualified name based on the first parameter
    @EventHandler
    public CompletableFuture<Void> on(SomePayload event) {

    }

    @EventHandler(qualifiedName = {ANY})
    public CompletableFuture<Void> on(String event) {

    }
    @EventHandler(payloadTypes = {ANY})
    public CompletableFuture<Void> on(Document event) {

    }
    @EventHandler(payloadTypes = {ANY})
    public CompletableFuture<Void> on(byte[] event) {

    }
    @EventHandler(payloadTypes = {ANY})
    public CompletableFuture<Void> on(JsonNode event) {

    }

    @ContentTypeConverter
    public CompletableFuture<JsonNode> convert(byte[] data) {
        //...
    }
}
* */
