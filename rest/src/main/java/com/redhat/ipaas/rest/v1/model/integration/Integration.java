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
package com.redhat.ipaas.rest.v1.model.integration;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.redhat.ipaas.rest.v1.model.*;
import com.redhat.ipaas.rest.v1.model.connection.Connection;
import com.redhat.ipaas.rest.v1.model.user.User;
import org.immutables.value.Value;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Value.Immutable
@JsonDeserialize(builder = Integration.Builder.class)
public interface Integration extends WithId<Integration>, WithName, Serializable {

    String KIND = "integration";

    // Labels used in config-map used for holding integrations
    enum Label {
        NAME("ipaas.redhat.com/integration/name",true),
        ID("ipaas.redhat.com/integration/id",false),
        TEMPLATE("ipaas.redhat.com/integration/template",false);

        private final boolean required;
        private final String label;

        Label(String label, boolean required) {
            this.label = label;
            this.required = required;
        }

        public String value() { return label; }
        public boolean isRequired() { return required; }
    }

    @Override
    default String getKind() {
        return KIND;
    }

    Optional<String> getConfiguration();

    Optional<String> getIntegrationTemplateId();

    Optional<IntegrationTemplate> getIntegrationTemplate();

    Optional<String> getUserId();

    List<User> getUsers();

    List<Tag> getTags();

    Optional<List<Connection>> getConnections();

    Optional<List<Step>> getSteps();

    Optional<String> getDescription();

    @Override
    default Integration withId(String id) {
        return new Builder().createFrom(this).id(id).build();
    }

    class Builder extends ImmutableIntegration.Builder {
    }

}
