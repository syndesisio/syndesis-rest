/*
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

package io.syndesis.model.integration;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

/**
 * An integration revision is a specific version of a certain integration. It belongs to
 * exactly one integration and has a certain state {@link IntegrationState}
 */
@Value.Immutable
@JsonDeserialize(builder = IntegrationRevisionSpec.Builder.class)
public interface IntegrationRevisionSpec {

    /**
     * Desired state of this revision
     */
    IntegrationState getState();

    /**
     * List of steps which build up this revision. Mandatory field.
     */
    List<Step> getSteps();

    // ============================================================

    class Builder extends ImmutableIntegrationRevisionSpec.Builder { }

}
