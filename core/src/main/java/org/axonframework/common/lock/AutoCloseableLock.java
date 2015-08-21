package org.axonframework.common.lock;

public interface AutoCloseableLock extends AutoCloseable{

    boolean lock();

    void unlock();

    default void close() {
        unlock();
    }
}
