/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.serialization.gson;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.axonframework.serialization.ContentTypeConverter;

import java.nio.charset.StandardCharsets;

public class ByteArrayToJsonNodeConverter implements ContentTypeConverter<byte[], JsonElement> {

    private final Gson gson;

    public ByteArrayToJsonNodeConverter(Gson gson) {
        this.gson = gson;
    }

    @Override
    public Class<byte[]> expectedSourceType() {
        return byte[].class;
    }

    @Override
    public Class<JsonElement> targetType() {
        return JsonElement.class;
    }

    @Override
    public JsonElement convert(byte[] original) {
        return gson.fromJson(new String(original, StandardCharsets.UTF_8), JsonElement.class);
    }
}
