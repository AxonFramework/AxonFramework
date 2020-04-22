package org.axonframework.axonserver.connector.utils;

import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.gson.GsonSerializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.stream.Stream;

public class SerializerParameterResolver implements ParameterResolver {

    public static final int SERIALIZER_COUNT = 3;

    static final Serializer[] SERIALIZERS = new Serializer[]{
            XStreamSerializer.defaultSerializer(),
            JacksonSerializer.defaultSerializer(),
            GsonSerializer.defaultSerializer()
    };

    public static Stream<Serializer> serializerStream() {
        return Stream.of(SERIALIZERS);
    }

    private int i = 0;

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return Serializer.class.equals(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        try {
            return SERIALIZERS[i++];
        } finally {
            i %= SERIALIZERS.length;
        }
    }
}
