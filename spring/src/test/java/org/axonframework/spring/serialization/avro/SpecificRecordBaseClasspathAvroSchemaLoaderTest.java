package org.axonframework.spring.serialization.avro;

import org.apache.avro.Schema;
import org.junit.jupiter.api.*;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
