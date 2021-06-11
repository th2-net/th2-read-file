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

package com.exactpro.th2.readfile.cfg;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import com.exactpro.th2.read.file.common.cfg.CommonFileReaderConfiguration;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class FileReaderConfiguration {
    @JsonProperty(required = true)
    private final Path filesDirectory;

    @JsonPropertyDescription("The regexp which will be used to filter files to process from specified directory")
    private Map<String, AliasConfiguration> aliases = Collections.emptyMap();

    @JsonPropertyDescription("The common part of the configuration. It is the same for all reads that uses the common core part")
    private CommonFileReaderConfiguration common = new CommonFileReaderConfiguration();

    private Duration pullingInterval = Duration.ofSeconds(5);

    @JsonCreator
    public FileReaderConfiguration(@JsonProperty("filesDirectory") Path filesDirectory) {
        this.filesDirectory = Objects.requireNonNull(filesDirectory, "'Files directory' parameter");
    }

    public Path getFilesDirectory() {
        return filesDirectory;
    }

    public Map<String, AliasConfiguration> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, AliasConfiguration> aliases) {
        this.aliases = aliases;
    }

    public CommonFileReaderConfiguration getCommon() {
        return common;
    }

    public void setCommon(CommonFileReaderConfiguration common) {
        this.common = common;
    }

    public Duration getPullingInterval() {
        return pullingInterval;
    }

    public void setPullingInterval(Duration pullingInterval) {
        this.pullingInterval = pullingInterval;
    }
}
