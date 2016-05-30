/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization;

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
    private final List<ContentTypeConverter<?, ?>> converters = new CopyOnWriteArrayList<>();

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

    @Override
    public <S, T> boolean hasConverter(Class<S> sourceContentType, Class<T> targetContentType) {
        if (sourceContentType.equals(targetContentType)) {
            return true;
        }
        for (ContentTypeConverter converter : converters) {
            if (canConvert(converter, sourceContentType, targetContentType)) {
                return true;
            }
        }
        return ChainedConverter.canConvert(sourceContentType, targetContentType, converters);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S, T> ContentTypeConverter<S, T> getConverter(Class<S> sourceContentType, Class<T> targetContentType) {
        if (sourceContentType.equals(targetContentType)) {
            return new NoConversion(sourceContentType);
        }
        for (ContentTypeConverter converter : converters) {
            if (canConvert(converter, sourceContentType, targetContentType)) {
                return converter;
            }
        }
        ChainedConverter<S, T> converter = ChainedConverter.calculateChain(sourceContentType, targetContentType,
                                                                           converters);
        converters.add(0, converter);
        return converter;
    }

    private <S, T> boolean canConvert(ContentTypeConverter<?, ?> converter, Class<S> sourceContentType,
                                      Class<T> targetContentType) {
        try {
            if (converter.expectedSourceType().isAssignableFrom(sourceContentType)
                    && targetContentType.isAssignableFrom(converter.targetType())) {
                return true;
            }
            // we do this call to make sure target Type is on the classpath
            converter.targetType();
        } catch (NoClassDefFoundError e) {
            logger.info("ContentTypeConverter [{}] is ignored. It seems to rely on a class that is "
                                + "not available in the class loader: {}", converter, e.getMessage());
            converters.remove(converter);
        }
        return false;
    }

    /**
     * Registers the given <code>converter</code> with this factory. The converter which is registered <em>last</em>
     * will be inspected <em>first</em> when finding a suitable converter for a given input and output type.
     * <p/>
     * An alternative to explicit converter registration (but without the ordering guarantees) is to create a file
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
     * Registers a convert of the given <code>converterType</code> with this factory, only if initialization of such a
     * converter is possible. Both the expected source type and target type classes are checked for availability on the
     * class path. In contrast to {@link #registerConverter(ContentTypeConverter)}, this method allows potentially
     * unsafe (in terms of class dependencies) converters to be registered.
     * <p/>
     * The converter which is registered <em>last</em> will be inspected <em>first</em> when finding a suitable
     * converter for a given input and output type.
     * <p/>
     * An alternative to explicit converter registration (but without the ordering guarantees) is to create a file
     * called <code>org.axonframework.serializer.ContentTypeConverter</code> in <code>/META-INF/services/</code> on the
     * class path which contains the fully qualified class names of the converters, separated by newlines. These
     * implementations must have a public no-arg constructor.
     *
     * @param converterType the type of converter to register.
     */
    public void registerConverter(Class<? extends ContentTypeConverter> converterType) {
        try {
            ContentTypeConverter converter = converterType.getConstructor().newInstance();
            converter.targetType();
            converter.expectedSourceType();
            registerConverter(converter);
        } catch (Exception e) {
            logger.warn("An exception occurred while trying to initialize a [{}].", converterType.getName(), e);
        } catch (NoClassDefFoundError e) {
            logger.info("ContentTypeConverter of type [{}] is ignored. It seems to rely on a class that is "
                                + "not available in the class loader: {}", converterType, e.getMessage());
        }
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
