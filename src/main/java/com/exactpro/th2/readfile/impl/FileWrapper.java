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

package com.exactpro.th2.readfile.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWrapper implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileWrapper.class);
    private final Path path;

    private boolean isRead;

    public FileWrapper(@NotNull Path path) {
        this.path = Objects.requireNonNull(path, "'Path' can't be null");
    }

    public byte[] readAllBytes() throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(path);
            this.isRead = true;
            return bytes;
        } catch (IOException e) {
            LOGGER.error("Error reading {} bytes", path, e);
            throw e;
        }
    }

    @Override
    public void close() {
    }

    public Path getPath() {
        return path;
    }

    public boolean isRead() {
        return isRead;
    }
}
