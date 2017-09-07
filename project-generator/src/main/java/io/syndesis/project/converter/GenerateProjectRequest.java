/**
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.project.converter;

import io.syndesis.model.connection.Connector;
import io.syndesis.model.integration.IntegrationSpec;
import org.immutables.value.Value;

import java.util.Map;
import java.util.Optional;

@Value.Immutable
public interface GenerateProjectRequest {

    Optional<String> getId();
    String getName();

    Optional<String> getDescription();

    IntegrationSpec getSpec();

    Map<String, Connector> getConnectors();

    String getGitHubUserLogin();
    String getGitHubUserName();
    String getGitHubUserEmail();
    String getGitHubRepoName();

    class Builder extends ImmutableGenerateProjectRequest.Builder {
    }

}
