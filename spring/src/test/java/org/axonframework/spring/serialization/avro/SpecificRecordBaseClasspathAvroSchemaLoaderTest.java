/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.spring.serialization.avro;

import org.apache.avro.Schema;
import org.junit.jupiter.api.*;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finds schemas declared in {@link org.apache.avro.specific.SpecificRecordBase} classes in test packages 1 and 2.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
class SpecificRecordBaseClasspathAvroSchemaLoaderTest {

    private SpecificRecordBaseClasspathAvroSchemaLoader testSubject;

    @Test
    void findsSchemas() {
        DefaultResourceLoader loader = new DefaultResourceLoader();
        testSubject = new SpecificRecordBaseClasspathAvroSchemaLoader(loader);
        List<String> packages = new ArrayList<>();
        packages.add("org.axonframework.spring.serialization.avro.test1");
        packages.add("org.axonframework.spring.serialization.avro.test2");
        packages.add("org.axonframework.spring.serialization.avro.doesntexist");
        List<Schema> schemas = testSubject.load(packages);
        assertEquals(2, schemas.size());

        List<Schema> expected = new ArrayList<>();
        expected.add(org.axonframework.spring.serialization.avro.test1.ComplexObject.getClassSchema());
        expected.add(org.axonframework.spring.serialization.avro.test2.ComplexObject.getClassSchema());
        assertIterableEquals(schemas, expected);
    }
}
