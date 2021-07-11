/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.common.grpc.RawMessage;
import com.exactpro.th2.common.grpc.RawMessage.Builder;
import com.exactpro.th2.read.file.common.ContentParser;
import com.exactpro.th2.read.file.common.StreamId;
import com.google.protobuf.ByteString;

public class FileParser implements ContentParser<FileWrapper> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileParser.class);

    @Override
    public boolean canParse(@NotNull StreamId streamId, FileWrapper fileWrapper, boolean considerNoFutureUpdates) {
        return considerNoFutureUpdates;
    }

    @Override
    public @NotNull Collection<Builder> parse(@NotNull StreamId streamId, FileWrapper fileWrapper) {
        try {
            byte[] bytes = Files.readAllBytes(fileWrapper.getPath());
            Builder rawMessageBuilder = RawMessage.newBuilder().setBody(ByteString.copyFrom(bytes));
            fileWrapper.setRead(true);
            return Collections.singletonList(rawMessageBuilder);
        } catch (IOException e) {
            LOGGER.error("Error reading {} bytes", fileWrapper.getPath(), e);
            return ExceptionUtils.rethrow(e);
        }
    }
}
