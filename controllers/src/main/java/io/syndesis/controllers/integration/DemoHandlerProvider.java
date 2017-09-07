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
package io.syndesis.controllers.integration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.syndesis.model.integration.Integration;

import io.syndesis.model.integration.IntegrationRevision;
import io.syndesis.model.integration.IntegrationState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "controllers.integration.enabled", havingValue = "false")
public class DemoHandlerProvider implements StatusChangeHandlerProvider {

       private static final List<StatusChangeHandler> HANDLERS = Arrays.asList(
            new DemoHandler(IntegrationState.Active, 5000L),
            new DemoHandler(IntegrationState.Inactive, 5000L),
            new DemoHandler(IntegrationState.Active, 5000L));


    @Override
    public List<StatusChangeHandler> getDraftStatusChangeHandlers() {
        return HANDLERS;
    }

    @Override
    public List<StatusChangeHandler> getDeployedStatusChangeHandlers() {
        return HANDLERS;
    }

    /* default */ static class DemoHandler implements StatusChangeHandler {
        private final IntegrationState state;
        private final long waitMillis;

        /* default */ DemoHandler(IntegrationState state, long waitMillis) {
            this.state = state;
            this.waitMillis = waitMillis;
        }

        public Set<IntegrationState> getTriggerStatuses() {
            return Collections.singleton(state);
        }

        @Override
        public StatusUpdate execute(Integration integration, IntegrationRevision revision) {
            Optional<IntegrationRevision> draft = integration.getDraftRevision();
            Optional<IntegrationRevision> deployed = integration.getDeployedRevision();

            Integer version = draft.map(dr -> dr.getVersion())
                .orElse(deployed.map(dl -> dl.getVersion()
                                .orElse(1)))
                .orElse(1);

            try {
                Thread.sleep(waitMillis);

                return new StatusUpdate(version, state);
            } catch (InterruptedException e) {
                return null;
            }
        }
    }
}
