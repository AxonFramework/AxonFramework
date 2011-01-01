/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serializer;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.StringAggregateIdentifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * XStream converter to minimize the amount of output used when serializing aggregate identifiers. Only the backing
 * identifier value is serialized.
 * <p/>
 * Deserialized instances will always have a {@link org.axonframework.domain.StringAggregateIdentifier} instance as
 * AggregateIdentifier.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AggregateIdentifierConverter implements Converter {

    @Override
    public boolean canConvert(Class type) {
        return AggregateIdentifier.class.isAssignableFrom(type);
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        writer.setValue(((AggregateIdentifier) source).asString());
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String idValue = reader.getValue();
        try {
            Constructor constructor = context.getRequiredType().getConstructor(String.class);
            return constructor.newInstance(idValue);
        } catch (NoSuchMethodException e) {
            return new StringAggregateIdentifier(idValue);
        } catch (InvocationTargetException e) {
            return new StringAggregateIdentifier(idValue);
        } catch (InstantiationException e) {
            return new StringAggregateIdentifier(idValue);
        } catch (IllegalAccessException e) {
            return new StringAggregateIdentifier(idValue);
        }
    }
}
