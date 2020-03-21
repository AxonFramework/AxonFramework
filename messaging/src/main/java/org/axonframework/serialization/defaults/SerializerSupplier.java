package org.axonframework.serialization.defaults;

import org.axonframework.serialization.Serializer;

public interface SerializerSupplier {

    Serializer getSerializer();
}
