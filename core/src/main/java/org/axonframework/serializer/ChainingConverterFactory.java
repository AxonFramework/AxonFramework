package org.axonframework.serializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ConverterFactory implementation that will combine converters to form chains of converters to be able to convert
 * from one type to another, for which there is no suitable single converter.
 * <p/>
 * This implementation will also autodetect ContentTypeConverter implementations by scanning
 * <code>/META-INF/services/org.axonframework.serializer.ContentTypeConverter</code> files on the classpath. These
 * files must contain the fully qualified class names of the implementations to use.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ChainingConverterFactory implements ConverterFactory {

    private static final Logger logger = LoggerFactory.getLogger(ChainingConverterFactory.class);
    private final List<ContentTypeConverter<?, ?>> converters = new CopyOnWriteArrayList<ContentTypeConverter<?, ?>>();

    /**
     * Initialize a new ChainingConverterFactory. Will autodetect all converters mentioned in
     * <code>/META-INF/services/org.axonframework.serializer.ContentTypeConverter</code> files on the class path.
     * <p/>
     * Instances of ChainingConverterFactory are safe for use in a multi-threaded environment, with exception of the
     * {@link #registerConverter(ContentTypeConverter)} method.
     */
    public ChainingConverterFactory() {
        ServiceLoader<ContentTypeConverter> converterLoader = ServiceLoader.load(ContentTypeConverter.class);
        for (ContentTypeConverter converter : converterLoader) {
            converters.add(converter);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S, T> ContentTypeConverter<S, T> getConverter(Class<S> sourceContentType, Class<T> targetContentType) {
        if (sourceContentType.equals(targetContentType)) {
            return new NoConversion(sourceContentType);
        }
        for (ContentTypeConverter converter : converters) {
            try {
                if (converter.expectedSourceType().isAssignableFrom(sourceContentType) &&
                        targetContentType.isAssignableFrom(converter.targetType())) {
                    return converter;
                }
            } catch (NoClassDefFoundError e) {
                logger.info("ContentTypeConverter [{}] is ignored. It seems to rely on a class that is "
                                    + "not available in the class loader: {}", converter, e.getMessage());
                converters.remove(converter);
            }
        }
        ChainedConverter<S, T> converter = ChainedConverter.calculateChain(sourceContentType, targetContentType,
                                                                           converters);
        converters.add(0, converter);
        return converter;
    }

    /**
     * Registers the given <code>converter</code> with this factory. The converter which is registered <em>last</em>
     * will be inspected <em>first</em> when finding a suitable converter for a given input and output type.
     * <p/>
     * An alternative to explicit converter registration (but without the ordering guarantees) is to creaate a file
     * called <code>org.axonframework.serializer.ContentTypeConverter</code> in <code>/META-INF/services/</code> on the
     * class path which contains the fully qualified class names of the converters, separated by newlines. These
     * implementations must have a public no-arg constructor.
     *
     * @param converter the converter to register.
     */
    public void registerConverter(ContentTypeConverter converter) {
        converters.add(0, converter);
    }

    /**
     * Setter for dependency injection frameworks that require property methods. This method is the same as calling
     * {@link #registerConverter(ContentTypeConverter)} for each converter in the given list of
     * <code>additionalConverters</code>.
     *
     * @param additionalConverters The converters to register with this factory
     */
    public void setAdditionalConverters(List<ContentTypeConverter> additionalConverters) {
        for (ContentTypeConverter converter : additionalConverters) {
            registerConverter(converter);
        }
    }

    private static class NoConversion<T> implements ContentTypeConverter<T, T> {

        private final Class<T> type;

        public NoConversion(Class<T> type) {
            this.type = type;
        }

        @Override
        public Class<T> expectedSourceType() {
            return type;
        }

        @Override
        public Class<T> targetType() {
            return type;
        }

        @Override
        public SerializedObject<T> convert(SerializedObject<T> original) {
            return original;
        }

        @Override
        public T convert(T original) {
            return original;
        }
    }
}
