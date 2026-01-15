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

import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import org.axonframework.conversion.json.JacksonConverter;

import java.beans.ConstructorProperties;
import java.util.Collection;
import java.util.EnumSet;

/**
 * Enumeration of converters for testing purposes.
 *
 * @author JohT
 */
@SuppressWarnings("unused")
public enum TestConverter {

    JACKSON {
        private final Converter converter = new JacksonConverter();

        @Override
        public Converter getConverter() {
            return converter;
        }
    },
    CBOR {
        private final Converter converter = new JacksonConverter(
                CBORMapper
                        .builder()
                        .findAndAddModules()
                        .build());

        @Override
        public Converter getConverter() {
            return converter;
        }
    },
    JACKSON_ONLY_ACCEPT_CONSTRUCTOR_PARAMETERS {
        private final Converter converter =
                new JacksonConverter(OnlyAcceptConstructorPropertiesAnnotation.attachTo(new ObjectMapper()));

        @Override
        public Converter getConverter() {
            return converter;
        }
    };

    protected byte[] serialize(Object object) {
        return getConverter().convert(object, byte[].class);
    }

    protected <T> T deserialize(byte[] serialized, Class<T> type) {
        return getConverter().convert(serialized, type);
    }

    public abstract Converter getConverter();

    @SuppressWarnings("unchecked")
    public <T> T serializeDeserialize(T object) {
        return deserialize(serialize(object), (Class<T>) object.getClass());
    }

    public static Collection<TestConverter> all() {
        return EnumSet.allOf(TestConverter.class);
    }

    public static class OnlyAcceptConstructorPropertiesAnnotation extends JacksonAnnotationIntrospector {

        public static ObjectMapper attachTo(ObjectMapper objectMapper) {
            return objectMapper.setAnnotationIntrospector(new OnlyAcceptConstructorPropertiesAnnotation());
        }

        @Override
        public Mode findCreatorAnnotation(MapperConfig<?> config, Annotated annotated) {
            return (annotated.hasAnnotation(ConstructorProperties.class))
                    ? super.findCreatorAnnotation(config, annotated)
                    : Mode.DISABLED;
        }
    }
}