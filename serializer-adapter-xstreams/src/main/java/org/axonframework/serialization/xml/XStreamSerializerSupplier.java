package org.axonframework.serialization.xml;

import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.defaults.DefaultSerializerSupplier;
import org.axonframework.serialization.SerializerBuilder;

import java.util.Map;

public class XStreamSerializerSupplier implements DefaultSerializerSupplier {
    @Override
    public Serializer getSerializer() {
        return XStreamSerializer.defaultSerializer();
    }

    @Override
    public SerializerBuilder getSerializerBuilder() {
        return new SerializerBuilder() {
            XStreamSerializer.Builder builder = XStreamSerializer.builder();

            @Override
            public SerializerBuilder revisionResolver(RevisionResolver revisionResolver) {
                builder.revisionResolver(revisionResolver);
                return this;
            }

            @Override
            public SerializerBuilder beanClassLoader(ClassLoader beanClassLoader) {
                // ignore
                return this;
            }

            @Override
            public SerializerBuilder externalInjections(Map<Class, Object> beansByClass) {
                // ignore
                return this;
            }

            @Override
            public Serializer build() {
                return builder.build();
            }
        };
    }
}
