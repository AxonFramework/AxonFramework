/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.extension.spring.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link JacksonPageDeserializer}.
 *
 * @author Theo Emanuelsson
 */
class JacksonPageDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Page.class, new JacksonPageDeserializer());
        objectMapper.registerModule(module);
    }

    @Test
    void deserializerWorksCorrectly() throws Exception {
        String json = "{"
                + "\"content\":[\"item1\",\"item2\",\"item3\"],"
                + "\"number\":0,"
                + "\"size\":3,"
                + "\"totalElements\":10"
                + "}";

        Page<?> page = objectMapper.readValue(json, Page.class);

        assertThat(page).isInstanceOf(PageImpl.class);
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getNumber()).isEqualTo(0);
        assertThat(page.getSize()).isEqualTo(3);
        assertThat(page.getTotalElements()).isEqualTo(10);
    }

    @Test
    void deserializerHandlesEmptyContent() throws Exception {
        String json = "{"
                + "\"content\":[],"
                + "\"number\":0,"
                + "\"size\":10,"
                + "\"totalElements\":0"
                + "}";

        Page<?> page = objectMapper.readValue(json, Page.class);

        assertThat(page).isInstanceOf(PageImpl.class);
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getNumber()).isEqualTo(0);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    @Test
    void deserializerHandlesMissingFields() throws Exception {
        String json = "{\"content\":[\"item1\",\"item2\"]}";

        Page<?> page = objectMapper.readValue(json, Page.class);

        assertThat(page).isInstanceOf(PageImpl.class);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getNumber()).isEqualTo(0);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void serializationRoundTrip() throws Exception {
        Page<String> originalPage = new PageImpl<>(
                List.of("item1", "item2", "item3"),
                PageRequest.of(0, 3),
                10
        );

        String json = objectMapper.writeValueAsString(originalPage);
        Page<?> deserializedPage = objectMapper.readValue(json, Page.class);

        assertThat(deserializedPage.getContent()).hasSize(originalPage.getContent().size());
        assertThat(deserializedPage.getNumber()).isEqualTo(originalPage.getNumber());
        assertThat(deserializedPage.getSize()).isEqualTo(originalPage.getSize());
        assertThat(deserializedPage.getTotalElements()).isEqualTo(originalPage.getTotalElements());
    }
}
