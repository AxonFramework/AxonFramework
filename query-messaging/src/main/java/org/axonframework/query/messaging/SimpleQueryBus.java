package org.axonframework.query.messaging;

import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlingContext;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.ProcessingContext;
import org.axonframework.messaging.ProcessingLifecycle;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.Registration;
import org.axonframework.messaging.UnitOfWork;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimpleQueryBus implements QueryBus {

    private final Map<Set<QualifiedName>, QueryHandler> queryHandlers = new HashMap<>();
    private final List<MessageHandlerInterceptor<QueryMessage, ?>> handlerInterceptors = new ArrayList<>();

    @Override
    public Registration registerHandler(Set<QualifiedName> messageTypes,
                                        QueryHandler handler) {
        return null;
    }

    @Override
    public Registration registerInterceptor(
            MessageHandlerInterceptor<QueryMessage, MessageStream<QueryResponseMessage>> interceptor) {
        return null;
    }

    @Override
    public MessageStream<QueryResponseMessage> dispatch(QueryMessage query) {
        return null;
    }

    private Publisher<QueryResponseMessage> handle(QueryMessage query) {
        // receive message
        // find handler
        QueryHandler queryHandler = queryHandlers.get(query.name());

        // create unit of work for handler and track result
        UnitOfWork unitOfWork = new UnitOfWork(query.identifier());

        // attach phases
        unitOfWork.on(
                ProcessingLifecycle.Phase.INVOCATION,
                context -> {
                    // process interceptors
                    // TODO interceptor chain goes here
//                    handlerInterceptors.forEach(handlerInterceptor -> handlerInterceptor.intercept(
//                            handlingContext -> handlingContext
//                    ));
                    // TODO Construct MessageHandlingContext
                    Object result = queryHandler.handle((MessageHandlingContext<QueryMessage>) query);
                    context.resources(ProcessingContext.ResourceScope.SHARED).put("result", result);
                    return null;
                });

        // execute unit of work
        // return result
        // TODO
        return Mono.fromCompletionStage(unitOfWork.start())
                   .thenMany(r -> unitOfWork.processingContext()
                                            .resources(ProcessingContext.ResourceScope.SHARED)
                                            .get("result")
                   );
    }
}
