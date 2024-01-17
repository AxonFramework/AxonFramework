package org.axonframework.messaging.unitofwork;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Awesome People
 */
public interface ProcessingContext extends ProcessingLifecycle {

    boolean isStarted();

    boolean isError();

    boolean isCommitted();

    boolean isCompleted();

    /**
     * Indicates whether a resource has been registered with the given {@code key} in this processing context
     *
     * @param key The key of the resource to check
     * @return {@code true} if a resource is registered with this key, otherwise {@code false}
     */
    boolean containsResource(ResourceKey<?> key);

    /**
     * Update the resource with given {@code key} using the given {@code updateFunction} to describe the update. If no
     * resource is registered with the given {@code key}, the updateFunction is invoked with {@code null}. Otherwise,
     * the function is called with the currently registered resource under that key.
     * <p>
     * The resource is replaced with the return value of the function, or removed when the function returns
     * {@code null}.
     * <p>
     * If the function throws an exception, the exception is rethrown to the caller.
     *
     * @param key            The key to update the resource for
     * @param updateFunction The function performing the update itself
     * @param <T>            the type of resource to update
     * @return the new value associated with the key, or {@code null} when removed
     */
    <T> T updateResource(ResourceKey<T> key, Function<T, T> updateFunction);

    /**
     * Returns the resource currently registered under given {@code key}, or {@code null} if no resource is present.
     *
     * @param key The key to retrieve the resource for
     * @param <T> The type of resource registered under given key
     * @return the resource currently registered under given {@code key}, or {@code null} if not present
     */
    <T> T getResource(ResourceKey<T> key);

    /**
     * If no resource is present for the given {@code key}, uses given {@code supplier} to supply the instance to
     * register.
     *
     * @param key      The key to register the resource for
     * @param supplier The function to supply the instance to register
     * @param <T>      The type of resource registered under given key
     * @return the resource associated with the key
     */
    <T> T computeResourceIfAbsent(ResourceKey<T> key, Supplier<T> supplier);

    /**
     * Register the given {@code instance} under the given {@code key}.
     *
     * @param key      The key under which to register the resource
     * @param instance The instance to register
     * @param <T>      The type of resource to register under given key
     * @return the previously registered instance, or {@code null} if none was present
     */
    <T> T putResource(ResourceKey<T> key, T instance);

    /**
     * Register the given {@code instance} under the given {@code key} if no value is currently present.
     *
     * @param key      The key under which to register the resource
     * @param newValue The value to register
     * @param <T>      The type of resource to register under given key
     * @return the resource previously associated with given key
     */
    <T> T putResourceIfAbsent(ResourceKey<T> key, T newValue);

    /**
     * Removes the resource registered under given {@code key}.
     *
     * @param key The key to remove the registered resource for
     * @param <T> The type of resource associated with the key
     * @return the value previously associated with the key
     */
    <T> T removeResource(ResourceKey<T> key);

    /**
     * Remove the resource associated with given {@code key} if the given {@code expectedInstance} is the currently
     * associated value.
     *
     * @param key              The key to remove the registered resource for
     * @param expectedInstance The expected instance to remove
     * @param <T>              The type of resource associated with the key
     * @return {@code true} if the resource has been removed, otherwise {@code false}
     */
    <T> boolean removeResource(ResourceKey<T> key, T expectedInstance);

    /**
     * Object that is used as a key to retrieve and register resources of a given type in a processing context.
     * <p>
     * Implementations are encouraged to override the {@code toString()} method to include some information useful for
     * debugging.
     * <p>
     * Instance of a ResourceKey can be created using {@link ResourceKey#create(String)}
     *
     * @param <T> The type of resource registered under this key
     */
    @SuppressWarnings("unused")
    final class ResourceKey<T> {

        private final String toString;

        private ResourceKey(String debugString) {
            String keyId = "Key@" + Integer.toHexString(System.identityHashCode(this));
            if (debugString == null || debugString.isBlank()) {
                this.toString = keyId;
            } else {
                this.toString = keyId + "[" + debugString + "]";
            }
        }

        /**
         * Create a new Key for a resource of given type {@code T}. The given {@code debugString} is part of the
         * {@code toString()} of the created key instance and may be used for debugging purposes.
         *
         * @param debugString A String to recognize this key during debugging
         * @param <T>         The type of resource to register under this key
         * @return a new key used to register and retrieve resources
         */
        public static <T> ResourceKey<T> create(String debugString) {
            return new ResourceKey<>(debugString);
        }

        @Override
        public String toString() {
            return toString;
        }
    }
}
