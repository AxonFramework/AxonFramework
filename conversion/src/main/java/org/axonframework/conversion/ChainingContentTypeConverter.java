/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.conversion;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link Converter} implementation that will combine {@link ContentTypeConverter ContentTypeConverters} to form
 * chains of converters to be able to convert from one type to another, for which there is no suitable single
 * converter.
 * <p/>
 * This implementation will also autodetect {@code ContentTypeConverter} implementations by scanning
 * {@code /META-INF/services/org.axonframework.conversion.ContentTypeConverter} files on the classpath. These files must
 * contain the fully qualified class names of the implementations to use.
 * <p>
 * Note that since this {@code Converter} acts on the {@code ContentTypeConverter}, and a {@code ContentTypeConverter}
 * only works with {@link Class Classes}, that the {@code ChainingContentTypeConverter} can only work with source and
 * target types that are a {@link Class}. Hence, if the {@link Type} that is given to {@link #canConvert(Type, Type)} or
 * {@link #convert(Object, Type)} is <b>not</b> a {@code Class}, those methods return early. In case of
 * {@code canConvert}, {@code false} will be returned. For {@code convert}, an {@link ConversionException} is thrown.
 *
 * @author Allard Buijze
 * @since 2.0.0
 */
public class ChainingContentTypeConverter implements Converter {

    private static final Logger logger = LoggerFactory.getLogger(ChainingContentTypeConverter.class);

    private final List<ContentTypeConverter<?, ?>> converters = new CopyOnWriteArrayList<>();

    /**
     * Initialize a new {@code ChainingConverter} with the context {@link ClassLoader} for
     * {@link Thread#currentThread() this thread}.
     * <p>
     * Will autodetect all {@link ContentTypeConverter ContentTypeConverters} mentioned in
     * {@code /META-INF/services/org.axonframework.conversion.ContentTypeConverter} files on the class path.
     * <p>
     * Instances of {@code ChainingConverter} are safe for use in a multithreaded environment, except for the
     * {@link #registerConverter(ContentTypeConverter)} method.
     */
    public ChainingContentTypeConverter() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Initialize a new {@code ChainingConverter} with the given {@code classLoader}.
     * <p>
     * Will autodetect all {@link ContentTypeConverter ContentTypeConverters} mentioned in
     * {@code /META-INF/services/org.axonframework.conversion.ContentTypeConverter} files on the class path.
     * <p/>
     * Instances of {@code ChainingConverter} are safe for use in a multithreaded environment, except for the
     * {@link #registerConverter(ContentTypeConverter)} method.
     *
     * @param classLoader The class loader used to load the {@link ContentTypeConverter ContentTypeConverters}.
     */
    public ChainingContentTypeConverter(@Nonnull ClassLoader classLoader) {
        //noinspection rawtypes
        ServiceLoader<ContentTypeConverter> converterLoader =
                ServiceLoader.load(ContentTypeConverter.class, classLoader);
        for (ContentTypeConverter<?, ?> converter : converterLoader) {
            converters.add(converter);
        }
    }

    /**
     * Indicates whether this {@code Converter} is capable of converting the given {@code sourceType} to the
     * {@code targetType}.
     *
     * @param sourceType The type of data to convert from.
     * @param targetType The type of data to convert to.
     * @return {@code true} if conversion is possible, {@code false} otherwise.
     */
    public boolean canConvert(@Nonnull Class<?> sourceType, @Nonnull Class<?> targetType) {
        return canConvert(sourceType, (Type) targetType);
    }

    /**
     * Indicates whether this {@code Converter} is capable of converting the given {@code sourceType} to the
     * {@code targetType}.
     *
     * @param sourceType The type of data to convert from.
     * @param targetType The type of data to convert to.
     * @return {@code true} if conversion is possible, {@code false} otherwise.
     */
    public boolean canConvert(@Nonnull Type sourceType, @Nonnull Type targetType) {
        if (sourceType.equals(targetType)) {
            return true;
        }

        if (!(sourceType instanceof Class<?> sourceClass) || !(targetType instanceof Class<?> targetClass)) {
            return false;
        }

        for (ContentTypeConverter<?, ?> converter : converters) {
            if (canConvert(converter, sourceClass, targetClass)) {
                return true;
            }
        }
        return ChainedConverter.canConvert(sourceClass, targetClass, converters);
    }

    @Override
    @Nullable
    public <T> T convert(@Nullable Object input, @Nonnull Type targetType) {
        if (input == null) {
            return null;
        }
        Class<?> sourceType = input.getClass();
        if (sourceType.equals(targetType)) {
            //noinspection unchecked
            return (T) input;
        }

        if (!(targetType instanceof Class<?> targetClass)) {
            throw new ConversionException(
                    "The targetType [" + targetType
                            + "] is not of type Class<?>, while the ChainingContentTypeConverter can only deal with Class<?>."
            );
        }

        for (ContentTypeConverter<?, ?> converter : converters) {
            if (canConvert(converter, input.getClass(), targetClass)) {
                //noinspection unchecked
                ContentTypeConverter<Object, T> typedConverter = (ContentTypeConverter<Object, T>) converter;
                return typedConverter.convert(input);
            }
        }

        //noinspection unchecked,rawtypes | calculateChain expects generic Class i.o. wildcard, which we cannot provide.
        ChainedConverter<Object, T> converter =
                ChainedConverter.calculateChain(input.getClass(), (Class) targetClass, converters);
        converters.addFirst(converter);
        return converter.convert(input);
    }

    private <S, T> boolean canConvert(ContentTypeConverter<?, ?> converter,
                                      Class<S> sourceContentType,
                                      Class<T> targetContentType) {
        try {
            if (converter.expectedSourceType().isAssignableFrom(sourceContentType)
                    && targetContentType.isAssignableFrom(converter.targetType())) {
                return true;
            }
            // we do this call to make sure target Type is on the classpath
            converter.targetType();
        } catch (NoClassDefFoundError e) {
            logger.info("ContentTypeConverter [{}] is ignored. It seems to rely on a class that is " +
                                "not available in the class loader: {}", converter, e.getMessage());
            converters.remove(converter);
        }
        return false;
    }

    /**
     * Registers the given {@link ContentTypeConverter} with this {@code ChainingConverter}.
     * <p>
     * The converter that is registered <em>last</em> will be inspected <em>first</em> when finding a suitable converter
     * for a given input and output type.
     * <p/>
     * An alternative to explicit converter registration (but without the ordering guarantees) is to create a file
     * called {@code org.axonframework.conversion.ContentTypeConverter} in {@code /META-INF/services/} on the class path
     * which contains the fully qualified class names of the converters, separated by newlines. These implementations
     * must have a public no-arg constructor.
     *
     * @param converter The converter to register with this {@code ChainingConverter}.
     */
    public void registerConverter(@Nonnull ContentTypeConverter<?, ?> converter) {
        converters.addFirst(converter);
    }

    /**
     * Registers a {@link ContentTypeConverter} of the given {@code converterType} with this factory, only if
     * initialization of such a converter is possible.
     * <p>
     * Both the expected source type and target type classes are checked for availability on the class path. In contrast
     * to {@link #registerConverter(ContentTypeConverter)}, this method allows potentially unsafe (in terms of class
     * dependencies) converters to be registered.
     * <p/>
     * The converter that is registered <em>last</em> will be inspected <em>first</em> when finding a suitable converter
     * for a given input and output type.
     * <p/>
     * An alternative to explicit converter registration (but without the ordering guarantees) is to create a file
     * called {@code org.axonframework.conversion.ContentTypeConverter} in {@code /META-INF/services/} on the class path
     * which contains the fully qualified class names of the converters, separated by newlines. These implementations
     * must have a public no-arg constructor.
     *
     * @param converterType The type of converter to register.
     */
    public void registerConverter(@Nonnull Class<? extends ContentTypeConverter<?, ?>> converterType) {
        try {
            ContentTypeConverter<?, ?> converter = converterType.getConstructor().newInstance();
            converter.targetType();
            converter.expectedSourceType();
            registerConverter(converter);
        } catch (Exception e) {
            logger.warn("An exception occurred while trying to initialize a [{}].", converterType.getName(), e);
        } catch (NoClassDefFoundError e) {
            logger.info("ContentTypeConverter of type [{}] is ignored. It seems to rely on a class that is " +
                                "not available in the class loader: {}", converterType, e.getMessage());
        }
    }

    /**
     * A {@link ContentTypeConverter} setter for dependency injection frameworks that require property methods.
     * <p>
     * This method is the same as calling {@link #registerConverter(ContentTypeConverter)} for each converter in the
     * given list of {@code additionalConverters}.
     *
     * @param additionalConverters The additional {@link ContentTypeConverter converters} to register with this
     *                             {@code ChainingConverter}.
     */
    public void setAdditionalConverters(@Nonnull List<ContentTypeConverter<?, ?>> additionalConverters) {
        additionalConverters.forEach(this::registerConverter);
    }

    /**
     * Retrieves the list of {@link ContentTypeConverter ContentTypeConverters} registered in this
     * {@code ChainingConverter} instance.
     *
     * @return Unmodified list of all {@link ContentTypeConverter ContentTypeConverters} registered with this
     * {@code ChainingConverter}.
     */
    @Nonnull
    public List<ContentTypeConverter<?, ?>> getContentTypeConverters() {
        return Collections.unmodifiableList(this.converters);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("content-type-converters", converters);
    }
}
