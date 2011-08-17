/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.gae.serializer;

import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.util.ClassLoaderReference;
import com.thoughtworks.xstream.core.util.CompositeClassLoader;
import com.thoughtworks.xstream.io.xml.XppDriver;
import org.springframework.beans.factory.FactoryBean;

/**
 * @author Jettro Coenradie
 */
public class XStreamFactory implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new GaeXStream(
                new PureJavaReflectionProvider(),
                new XppDriver(),
                new ClassLoaderReference(new CompositeClassLoader()));
    }

    @Override
    public Class<?> getObjectType() {
        return GaeXStream.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}
