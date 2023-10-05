/*
 * Copyright 2020-2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.readfile.cfg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import com.exactpro.th2.common.schema.factory.AbstractCommonFactory;
import com.exactpro.th2.common.schema.factory.CommonFactory;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class TestFileReaderConfiguration {
    @Test
    void testDeserializes() throws IOException {
        // we suppress the warning because the CommonFactory updates the mapper from AbstractCommonFactory
        // so, we need to access it via CommonFactory
        @SuppressWarnings("StaticFieldReferencedViaSubclass")
        ObjectMapper objectMapper = CommonFactory.MAPPER;
        try (var input = TestFileReaderConfiguration.class.getClassLoader().getResourceAsStream("test_cfg.json")) {
            FileReaderConfiguration cfg = objectMapper.readValue(input, FileReaderConfiguration.class);
            assertEquals(Path.of("files/dir").toString(), cfg.getFilesDirectory().toString());
            assertEquals(Set.of("A", "B", "C"), cfg.getAliases().keySet());
            assertEquals(Duration.ofSeconds(5), cfg.getPullingInterval());
        }
    }
}