package org.axonframework.serializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.ServiceLoader;

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
    private final Deque<ContentTypeConverter<?, ?>> converters = new ArrayDeque<ContentTypeConverter<?, ?>>();

    /**
     * Initialize a new ChainingConverterFactory. Will autodetect all converters mentioned in
     * <code>/META-INF/services/org.axonframework.serializer.ContentTypeConverter</code> files on the class path.
     */
    public ChainingConverterFactory() {
        ServiceLoader<ContentTypeConverter> converterLoader = ServiceLoader.load(ContentTypeConverter.class);
        for (ContentTypeConverter converter : converterLoader) {
            converters.addFirst(converter);
        }
    }

    @Override
    public <S, T> ContentTypeConverter getConverter(Class<S> sourceContentType, Class<T> targetContentType) {
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
        converters.addFirst(converter);
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
        converters.addFirst(converter);
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
            converters.addFirst(converter);
        }
    }
}
