package org.axonframework.upcasting;

import org.axonframework.serializer.ChainingConverterFactory;
import org.axonframework.serializer.ConverterFactory;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;

import java.util.ArrayList;
import java.util.List;

/**
 * UpcasterChain implementation that delays the actual upcasting until the data inside the returned SerializedObject is
 * actually fetched. This makes this implementation perform better in cases where not all events are used.
 * <p/>
 * <em>Ordering guarantees:</em><br/>
 * This upcasterChain only guarantees that for each of the configured upcasters, all previous upcasters have had the
 * opportunity to upcast the SerializedObject first. This UpcasterChain does <em>not</em> guarantee that, for each
 * upcaster, all events are upcast in the order they are provided in the stream.
 * <p/>
 * Note that this implementation is not suitable if you require each upcaster to upcast all events in a stream in
 * order. See {@link SimpleUpcasterChain} instead for these cases.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class LazyUpcasterChain extends AbstractUpcasterChain {

    /**
     * Initializes the UpcasterChain with given <code>upcasters</code> and a {@link ChainingConverterFactory} to
     * convert between content types.
     *
     * @param upcasters the upcasters to form the chain
     */
    public LazyUpcasterChain(List<Upcaster> upcasters) {
        this(new ChainingConverterFactory(), upcasters);
    }

    /**
     * Initializes the UpcasterChain with given <code>converterFactory</code> and <code>upcasters</code>.
     *
     * @param converterFactory The factory providing the converters to convert between content types
     * @param upcasters        The upcasters to form this chain
     */
    public LazyUpcasterChain(ConverterFactory converterFactory, List<Upcaster> upcasters) {
        super(converterFactory, upcasters);
    }

    @Override
    protected <T> List<SerializedObject<?>> doUpcast(Upcaster<T> upcaster,
                                                     SerializedObject<?> sourceObject,
                                                     List<SerializedType> targetTypes) {
        LazyUpcastObject<T> lazyUpcastObject = new LazyUpcastObject<T>(sourceObject, targetTypes, upcaster);
        List<SerializedObject<?>> upcastObjects = new ArrayList<SerializedObject<?>>(targetTypes.size());
        int t = 0;
        for (SerializedType serializedType : targetTypes) {
            upcastObjects.add(new LazyUpcastingSerializedObject<T>(t, lazyUpcastObject, serializedType));
            t++;
        }
        return upcastObjects;
    }

    private class LazyUpcastObject<T> {

        private final SerializedObject<?> serializedObject;
        private final List<SerializedType> upcastTypes;
        private final Upcaster<T> currentUpcaster;
        private volatile List<SerializedObject<?>> upcastObjects = null;

        public LazyUpcastObject(SerializedObject<?> serializedObject, List<SerializedType> upcastTypes,
                                Upcaster<T> currentUpcaster) {
            this.serializedObject = serializedObject;
            this.upcastTypes = upcastTypes;
            this.currentUpcaster = currentUpcaster;
        }

        public List<SerializedObject<?>> getUpcastSerializedObjects() {
            if (upcastObjects == null) {
                SerializedObject<T> converted = ensureCorrectContentType(serializedObject,
                                                                         currentUpcaster.expectedRepresentationType());
                upcastObjects = currentUpcaster.upcast(converted, upcastTypes);
            }
            return upcastObjects;
        }
    }

    private static class LazyUpcastingSerializedObject<T> implements SerializedObject {

        private final int index;
        private final LazyUpcastObject<T> lazyUpcastObject;
        private final SerializedType type;

        public LazyUpcastingSerializedObject(int index, LazyUpcastObject<T> lazyUpcastObject, SerializedType type) {
            this.index = index;
            this.lazyUpcastObject = lazyUpcastObject;
            this.type = type;
        }

        @Override
        public Class<?> getContentType() {
            return lazyUpcastObject.getUpcastSerializedObjects().get(index).getContentType();
        }

        @Override
        public SerializedType getType() {
            return type;
        }

        @Override
        public Object getData() {
            return lazyUpcastObject.getUpcastSerializedObjects().get(index).getData();
        }
    }
}
