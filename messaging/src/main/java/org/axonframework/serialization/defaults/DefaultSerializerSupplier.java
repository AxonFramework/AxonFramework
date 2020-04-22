package org.axonframework.serialization.defaults;

import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SerializerBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

public interface DefaultSerializerSupplier extends
        SerializerSupplier,
        SerializerBuilderSupplier {

    Supplier<DefaultSerializerSupplier> DEFAULT_SERIALIZER_SUPPLIER = () -> {
        Iterator<DefaultSerializerSupplier> iter = ServiceLoader.load(DefaultSerializerSupplier.class).iterator();
        List<DefaultSerializerSupplier> serializerSuppliers = new ArrayList<>(1);

        while (iter.hasNext())
            serializerSuppliers.add(iter.next());

        if (serializerSuppliers.size() != 1)
            throw new IllegalStateException("Found none or multiple default supplier for serializer.");
        else
            return serializerSuppliers.get(0);
    };

    Supplier<Serializer> DEFAULT_SERIALIZER = () -> DEFAULT_SERIALIZER_SUPPLIER.get().getSerializer();

    Supplier<SerializerBuilder> DEFAULT_SERIALIZER_BUILDER = () -> DEFAULT_SERIALIZER_SUPPLIER.get().getSerializerBuilder();
}
