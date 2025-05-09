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

import java.util.List;

/**
 * Classpath Avro schema loader.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public interface ClasspathAvroSchemaLoader {

    /**
     * Scans provided packages and loads schemas from classes.
     *
     * @param packageNames packages to scan.
     * @return list of detected Avro schemas.
     */
    List<Schema> load(List<String> packageNames);
}
