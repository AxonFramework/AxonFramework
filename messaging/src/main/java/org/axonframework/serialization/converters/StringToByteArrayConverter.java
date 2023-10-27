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

import java.nio.charset.Charset;

/**
 * ContentTypeConverter that converts String into byte arrays. Conversion is done using the UTF-8 character set.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class StringToByteArrayConverter implements ContentTypeConverter<String,byte[]> {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Override
    public Class<String> expectedSourceType() {
        return String.class;
    }

    @Override
    public Class<byte[]> targetType() {
        return byte[].class;
    }

    @Override
    public byte[] convert(String original) {
        return original.getBytes(UTF8);
    }
}
