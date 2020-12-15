package org.axonframework.queryhandling;

/**
 * Abstraction interface to bridge old FluxSink and SinksMany API
 * with common API..
 *
 * @author Stefan Dragisic
 * @since 4.5
 */
public interface SinkWrapper<T> {

    /**
     * Wrapper around Sink complete().
     */
    public void complete();

    /**
     * Wrapper around Sink next(Object).
     *
     * @param value to be passed to the delegate sink
     */
    public void next(T value);

    /**
     * Wrapper around Sink error(Throwable).
     *
     * @param t to be passed to the delegate sink
     */
    public void error(Throwable t);


}
