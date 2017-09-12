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
package io.syndesis.controllers.integration.online;

import java.util.Collections;
import java.util.Set;

import io.syndesis.controllers.integration.StatusChangeHandler;
import io.syndesis.controllers.integration.StatusUpdate;
import io.syndesis.core.Tokens;
import io.syndesis.model.integration.Integration;
import io.syndesis.model.integration.IntegrationRevision;
import io.syndesis.model.integration.IntegrationState;
import io.syndesis.openshift.OpenShiftDeployment;
import io.syndesis.openshift.OpenShiftService;

public class DeleteHandler implements StatusChangeHandler {

    private final OpenShiftService openShiftService;

    DeleteHandler(OpenShiftService openShiftService) {
        this.openShiftService = openShiftService;
    }

    @Override
    public Set<IntegrationState> getTriggerStatuses() {
        return Collections.singleton(IntegrationState.Undeployed);
    }

    @Override
    public StatusUpdate execute(Integration integration, IntegrationRevision revision) {
        //It's possible that we delete a draft version that never got deployed. In this case the version will be 0.
        Integer version = revision.getVersion().orElse(0);

        if (version == 0) {
            return new StatusUpdate(0, IntegrationState.Undeployed);
        }

        String token = integration.getToken().get();
        Tokens.setAuthenticationToken(token);

        OpenShiftDeployment deployment = OpenShiftDeployment
            .builder()
            .revisionNumber(version)
            .name(integration.getName())
            .token(token)
            .build();

        IntegrationState currentStatus = !openShiftService.exists(deployment)
            || openShiftService.delete(deployment)
            ? IntegrationState.Undeployed
            : IntegrationState.Pending;

        return new StatusUpdate(version, currentStatus);
    }
}
