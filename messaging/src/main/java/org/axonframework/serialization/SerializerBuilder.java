package org.axonframework.serialization;

import java.util.Map;

public interface SerializerBuilder {
    SerializerBuilder revisionResolver(RevisionResolver revisionResolver);

    SerializerBuilder beanClassLoader(ClassLoader beanClassLoader);

    SerializerBuilder externalInjections(Map<Class, Object> beansByClass);

    Serializer build();
}
