package org.axonframework.messaging.unitofwork;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class InheritableResources implements Resources {

    private final LocalResources localResources;
    private final Resources inheritedResources;

    public InheritableResources(LocalResources localResources, Resources inheritedResources) {
        this.localResources = localResources;
        this.inheritedResources = inheritedResources;
    }

    @Override
    public boolean contains(Object key) {
        return localResources.contains(key) || inheritedResources.contains(key);
    }

    @Override
    public <T> T get(Object key) {
        T local = localResources.get(key);
        if (local != null) {
            return local;
        }
        return inheritedResources.get(key);
    }

    @Override
    public <T, K> T computeIfAbsent(K key, Function<K, T> instance) {
        return localOrInherited(key, () -> localResources.computeIfAbsent(key, instance));
    }

    private <T, K> T localOrInherited(K key, Supplier<T> otherwise) {
        if (localResources.contains(key)) {
            return localResources.get(key);
        } else if (inheritedResources.contains(key)) {
            return inheritedResources.get(key);
        } else {
            return otherwise.get();
        }
    }

    @Override
    public <T> T getOrDefault(Object key, T defaultValue) {
        return localOrInherited(key, () -> localResources.getOrDefault(key, defaultValue));
    }

    @Override
    public void put(Object key, Object instance) {
        localResources.put(key, instance);
    }

    @Override
    public <T> T putIfAbsent(Object key, T newValue) {
        return localOrInherited(key, () -> localResources.putIfAbsent(key, newValue));
    }

    @Override
    public Object remove(Object key) {
        return localResources.remove(key);
    }

    @Override
    public <T> boolean remove(Object key, T expectedInstance) {
        return localResources.remove(key, expectedInstance);
    }

    @Override
    public void forEach(BiConsumer<Object, Object> resourceConsumer) {
        inheritedResources.forEach(resourceConsumer);
        localResources.forEach(resourceConsumer);
    }

    @Override
    public Map<Object, Object> getAll() {
        Map<Object, Object> inheritedResourcesMap = inheritedResources.getAll();
        inheritedResourcesMap.putAll(localResources.getAll());
        return inheritedResourcesMap;
    }
}
