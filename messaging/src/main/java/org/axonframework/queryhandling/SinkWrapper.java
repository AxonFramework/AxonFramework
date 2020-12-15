package org.axonframework.queryhandling;

/**
 * @author Stefan Dragisic
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
