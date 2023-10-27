package org.axonframework.messaging;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class LocalResources implements Resources {

    private final ConcurrentMap<Object, Object> resources = new ConcurrentHashMap<>();

    @Override
    public boolean contains(Object key) {
        return resources.containsKey(key);
    }

    @Override
    public <T> T get(Object key) {
        //noinspection unchecked
        return (T) resources.get(key);
    }

    @Override
    public <T, K> T computeIfAbsent(K key, Function<K, T> instance) {
        //noinspection unchecked
        return (T) resources.computeIfAbsent(key, k -> instance.apply((K) k));
    }

    @Override
    public <T> T getOrDefault(Object key, T defaultValue) {
        //noinspection unchecked
        return (T) resources.getOrDefault(key, defaultValue);
    }

    @Override
    public void put(Object key, Object instance) {
        resources.put(key, instance);
    }

    @Override
    public <T> T putIfAbsent(Object key, T newValue) {
        //noinspection unchecked
        return (T) resources.putIfAbsent(key, newValue);
    }

    @Override
    public Object remove(Object key) {
        return resources.remove(key);
    }

    @Override
    public <T> boolean remove(Object key, T expectedInstance) {
        return false;
    }

    @Override
    public void forEach(BiConsumer<Object, Object> resourceConsumer) {
        resources.forEach(resourceConsumer);
    }

    @Override
    public Map<Object, Object> getAll() {
        return new HashMap<>(resources);
    }
}
