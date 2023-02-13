package org.axonframework.axonserver.connector.utils;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * A gRPC {@link ServerInterceptor} setting the context to the {@link Context}. Uses the key {@code "AxonIQ-Context"} to
 * attach the current context.
 *
 * @author Marc Gathier
 */
public class ContextInterceptor implements ServerInterceptor {

    @Override
    public <T, R> ServerCall.Listener<T> interceptCall(ServerCall<T, R> serverCall,
                                                       Metadata metadata,
                                                       ServerCallHandler<T, R> serverCallHandler) {
        String context = metadata.get(PlatformService.AXON_IQ_CONTEXT);
        if (context == null) {
            context = "default";
        }
        Context updatedGrpcContext = Context.current()
                                            .withValue(PlatformService.CONTEXT_KEY, context);
        return Contexts.interceptCall(updatedGrpcContext, serverCall, metadata, serverCallHandler);
    }
}
