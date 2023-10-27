/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.serialization.converters;

import org.axonframework.serialization.ContentTypeConverter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * ContentTypeConverter that converts byte arrays into InputStream. More specifically, it returns an
 * ByteArrayInputStream with the underlying byte[] is backing data.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ByteArrayToInputStreamConverter implements ContentTypeConverter<byte[],InputStream> {

    @Override
    public Class<byte[]> expectedSourceType() {
        return byte[].class;
    }

    @Override
    public Class<InputStream> targetType() {
        return InputStream.class;
    }

    @Override
    public InputStream convert(byte[] original) {
        return new ByteArrayInputStream(original);
    }
}
