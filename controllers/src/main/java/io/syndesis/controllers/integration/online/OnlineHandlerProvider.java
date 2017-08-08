/**
 * Copyright (C) 2016 Red Hat, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.controllers.integration.online;

import java.util.Arrays;
import java.util.List;

import io.syndesis.controllers.integration.StatusChangeHandler;
import io.syndesis.controllers.integration.StatusChangeHandlerProvider;
import io.syndesis.dao.manager.DataManager;
import io.syndesis.github.GitHubService;
import io.syndesis.openshift.OpenShiftService;
import io.syndesis.project.converter.ProjectGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "controllers.integration.enabled", havingValue = "true", matchIfMissing = true)
public class OnlineHandlerProvider implements StatusChangeHandlerProvider {

    private final List<StatusChangeHandler> draftHandlers;

    private final List<StatusChangeHandler> deployedHandlers;

    public OnlineHandlerProvider(DataManager dataManager, OpenShiftService openShiftService,
                                 GitHubService gitHubService, ProjectGenerator projectConverter) {

        StatusChangeHandler activateHandler = new ActivateHandler(dataManager, openShiftService, gitHubService, projectConverter);
        StatusChangeHandler deactivateHandler = new DeactivateHandler(openShiftService);
        StatusChangeHandler deleteHandler = new DeleteHandler(openShiftService);

        this.draftHandlers = Arrays.asList(activateHandler, deleteHandler);
        this.deployedHandlers = Arrays.asList(deactivateHandler, deleteHandler);
    }

    @Override
    public List<StatusChangeHandler> getDraftStatusChangeHandlers() {
        return draftHandlers;
    }

    @Override
    public List<StatusChangeHandler> getDeployedStatusChangeHandlers() {
        return deployedHandlers;
    }
}
