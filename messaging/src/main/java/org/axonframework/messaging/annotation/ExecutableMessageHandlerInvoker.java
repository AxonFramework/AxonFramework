package org.axonframework.messaging.annotation;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.messaging.Message;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class ExecutableMessageHandlerInvoker<T> implements MessageHandlerInvoker<T> {

    private final Executable executable;
    private final Class<? extends Message<?>> messageType;

    @SuppressWarnings("deprecation") // Suppressed ReflectionUtils#ensureAccessible
    public ExecutableMessageHandlerInvoker(Executable executable,
                                           Class<? extends Message<?>> messageType){
        this.executable = executable;
        this.messageType = messageType;
        ReflectionUtils.ensureAccessible(this.executable);
    }

    @Override
    public CompletableFuture<Object> invoke(T target, List<Object> objects) {
        Object invocationResult;
        try {
            if (executable instanceof Method) {
                invocationResult = ((Method) executable).invoke(target, objects.toArray());
            } else if (executable instanceof Constructor) {
                invocationResult = ((Constructor<?>) executable).newInstance(objects.toArray());
            } else {
                return CompletableFuture.failedFuture(new IllegalStateException("What kind of handler is this?"));
            }
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            if (e.getCause() instanceof Exception) {
                return CompletableFuture.failedFuture(e.getCause());
            } else if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            return CompletableFuture.failedFuture(new MessageHandlerInvocationException(String.format(
                    "Error handling an object of type [%s]", messageType), e
            ));
        }
        if (invocationResult instanceof CompletableFuture<?>) {
            return (CompletableFuture<Object>) invocationResult;
        } else {
            return CompletableFuture.completedFuture(invocationResult);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + executable.toGenericString();
    }

    public Executable getExecutable() {
        return executable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExecutableMessageHandlerInvoker<?> that = (ExecutableMessageHandlerInvoker<?>) o;
        return Objects.equals(executable, that.executable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executable);
    }
}
