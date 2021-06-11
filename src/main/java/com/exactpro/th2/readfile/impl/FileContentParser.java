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

import java.io.BufferedReader;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.common.grpc.RawMessage.Builder;
import com.exactpro.th2.read.file.common.StreamId;
import com.exactpro.th2.read.file.common.impl.LineParser;

public class FileContentParser extends LineParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileContentParser.class);

    @NotNull
    @Override
    public Collection<Builder> parse(@NotNull StreamId streamId, @NotNull BufferedReader source) {
        var lines = source.lines().collect(Collectors.joining(System.lineSeparator()));
        return Collections.singletonList(lineToBuilder(lines));
    }
}
