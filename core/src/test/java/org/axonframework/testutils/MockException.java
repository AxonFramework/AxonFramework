package org.axonframework.testutils;

/**
 * Mock exception that provides no stack trace.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MockException extends RuntimeException {

    public MockException() {
        super("Mock");
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return new StackTraceElement[]{};
    }
}
