package org.axonframework.messaging;

/**
 * The interceptor chain manages the flow of a message through a chain of interceptors and ultimately to the message
 * handler. Interceptors may continue processing via this chain by calling the {@link #proceed()} method.
 * Alternatively, they can block processing by returning without calling either of these methods.
 *
 * @author Allard Buijze
 * @since 0.5
 */
@FunctionalInterface
public interface InterceptorChain {

    /**
     * Signals the Interceptor Chain to continue processing the message.
     *
     * @return The return value of the message processing
     * @throws Exception any exceptions thrown by interceptors or the message handler
     */
    Object proceed() throws Exception;

}
