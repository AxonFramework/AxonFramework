package org.axonframework.messaging.unitofwork;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


public interface Resources {

    boolean contains(Object key);

    default <T> T get(Class<T> key) {
        return get((Object) key);
    }

    <T> T get(Object key);

    default <T> T computeIfAbsent(Class<T> key, Supplier<T> instance) {
        return computeIfAbsent(key, k -> instance.get());
    }

    default <T> T computeIfAbsent(Object key, Supplier<T> instance) {
        return computeIfAbsent(key, k -> instance.get());
    }

    default <T> T computeIfAbsent(Class<T> key, Function<Class<T>, T> instance) {
        //noinspection unchecked
        return computeIfAbsent((Object) key, k -> instance.apply((Class<T>) k));
    }

    <T, K> T computeIfAbsent(K key, Function<K, T> instance);

    default <T> T computeAndInitializeResourceIfAbsent(Class<T> key, Supplier<T> ifAbsent, Consumer<T> initialization) {
        return computeAndInitializeResourceIfAbsent((Object) key, ifAbsent, initialization);
    }

    default <T> T computeAndInitializeResourceIfAbsent(Object key, Supplier<T> ifAbsent, Consumer<T> initialization) {
        T actual = get(key);
        if (actual == null) {
            T newValue = ifAbsent.get();
            actual = (T) putIfAbsent(key, newValue);
            if (actual == null) {
                initialization.accept(newValue);
                return newValue;
            }
        }
        return actual;
    }

    default <T> T getOrDefault(Class<T> key, T defaultValue) {
        return getOrDefault((Object) key, defaultValue);
    }

    <T> T getOrDefault(Object key, T defaultValue);

    default <T> void put(Class<T> key, T instance) {
        put((Object) key, instance);
    }

    void put(Object key, Object instance);

    default <T> T putIfAbsent(Class<T> key, T newValue) {
        return putIfAbsent((Object) key, newValue);
    }

    <T> T putIfAbsent(Object key, T newValue);

    Object remove(Object key);

    default <T> boolean remove(Class<T> key, T expectedInstance) {
        return remove((Object) key, expectedInstance);
    }

    <T> boolean remove(Object key, T expectedInstance);

    void forEach(BiConsumer<Object, Object> resourceConsumer);

    Map<Object, Object> getAll();
}
