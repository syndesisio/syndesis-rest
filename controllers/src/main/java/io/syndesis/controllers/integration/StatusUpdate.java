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

package io.syndesis.controllers.integration;

import io.syndesis.model.integration.IntegrationState;

import java.util.List;

public class StatusUpdate {

    private Integer version;
    private IntegrationState state;
    private String statusMessage;
    private List<String> stepsPerformed;

    public StatusUpdate(Integer version, IntegrationState state, String statusMessage, List<String> stepsPerformed) {
        this.version = version;
        this.state = state;
        this.statusMessage = statusMessage;
        this.stepsPerformed = stepsPerformed;
    }

    public StatusUpdate(Integer version, IntegrationState state, String statusMessage) {
        this(version, state, statusMessage, null);
    }

    public StatusUpdate(Integer version, IntegrationState state) {
        this(version, state, null, null);
    }

    public StatusUpdate(Integer version, IntegrationState state, List<String> stepsPerformed) {
        this(version, state, null, stepsPerformed != null && !stepsPerformed.isEmpty() ? stepsPerformed : null);
    }

    public Integer getVesion() {
        return version;
    }

    public IntegrationState getState() {
        return state;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public List<String> getStepsPerformed() {
        return stepsPerformed;
    }

    public StatusUpdate withError(Throwable t) {
        return new StatusUpdate(version, IntegrationState.Error, t.getMessage());
    }
}
