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

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.syndesis.model.Kind;
import io.syndesis.model.WithKind;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = IntegrationRevision.Builder.class)
public interface IntegrationRevision extends WithKind {

    @Override
    default Kind getKind() {
        return Kind.IntegrationRevision;
    }

    /**
     * Specification of this revision
     */
    IntegrationRevisionSpec getSpec();

    /**
     * The actual status of the integration revision
     */
    Optional<IntegrationRevisionStatus> getStatus();


    // ===================================================================
    // Builder methods

    default IntegrationRevision withVersion(Integer version) {
        IntegrationRevisionStatus.Builder builder = new IntegrationRevisionStatus.Builder();
        IntegrationRevisionStatus status =
            getStatus().map(builder::createFrom).orElse(builder).version(version).build();
        return new IntegrationRevision.Builder().createFrom(this).status(status).build();
    }

    default IntegrationRevision withCurrentState(IntegrationState state) {
        IntegrationRevisionStatus.Builder builder = new IntegrationRevisionStatus.Builder();
        IntegrationRevisionStatus status =
            getStatus().map(builder::createFrom).orElse(builder).state(state).build();
        return new IntegrationRevision.Builder().createFrom(this).status(status).build();
    }

    default IntegrationRevision withCurrentState(IntegrationState state, String message) {
        IntegrationRevisionStatus.Builder builder = new IntegrationRevisionStatus.Builder();
        IntegrationRevisionStatus status =
            getStatus().map(builder::createFrom).orElse(builder)
                       .state(state)
                       .message(message)
                       .build();
        return new IntegrationRevision.Builder().createFrom(this).status(status).build();
    }

    class Builder extends ImmutableIntegrationRevision.Builder { }

    // ===================================================================
    // Convenient access functions

    /**
     * The revision number. This is unique per {@link Integration}.
     * Once an {@link IntegrationRevision} gets a version, it should not be mutated anymore.
     */
    @JsonIgnore
    default Optional<Integer> getVersion() {
        return getStatus().flatMap(IntegrationRevisionStatus::getVersion);
    }

    /**
     * The desired state of the revision.
     */
    @JsonIgnore
    default IntegrationState getDesiredState() {
        return getSpec().getState();
    }

    /**
     * The current state of the revision.
     */
    @JsonIgnore
    default IntegrationState getCurrentState() {
        return getStatus().map(IntegrationRevisionStatus::getState).orElse(IntegrationState.Draft);
    }

    /**
     * Returns that {@link IntegrationState}.
     * The state is either the `desired state` or `pending`.
     * @return true, if current state is matching with target, false otherwise.
     */
    @JsonIgnore
    default boolean isPending() {
        return getDesiredState() != getCurrentState();
    }
}
