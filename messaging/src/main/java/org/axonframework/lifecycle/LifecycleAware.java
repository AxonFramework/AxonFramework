package org.axonframework.lifecycle;

import java.util.concurrent.CompletableFuture;

public interface LifecycleAware {

    void registerLifecycleHandlers(LifecycleRegistry handle);

    interface LifecycleRegistry {

        default void onStartup(int phase, Runnable action) {
            onStartup(phase, () -> {
                action.run();
                return CompletableFuture.completedFuture(null);
            });
        }

        void onShutdown(int phase, Runnable action);

        void onStartup(int phase, LifecycleHandler action);

        void onShutdown(int phase, LifecycleHandler action);

    }

    interface LifecycleHandler {

        CompletableFuture<?> run();
    }
}
