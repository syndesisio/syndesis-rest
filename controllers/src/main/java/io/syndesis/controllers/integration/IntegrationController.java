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

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import io.syndesis.core.EventBus;
import io.syndesis.core.Json;
import io.syndesis.dao.manager.DataManager;
import io.syndesis.model.ChangeEvent;
import io.syndesis.model.Kind;
import io.syndesis.model.integration.Integration;

import io.syndesis.model.integration.IntegrationRevision;
import io.syndesis.model.integration.IntegrationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * This class tracks changes to Integrations and attempts to process them so that
 * their current status matches their desired status.
 */
@Service
public class IntegrationController {
    private static final Logger LOG = LoggerFactory.getLogger(IntegrationController.class);

    private final DataManager dataManager;
    private final EventBus eventBus;
    private final Map<IntegrationState, StatusChangeHandler> draftHandlers;
    private final Map<IntegrationState, StatusChangeHandler> deployedHandlers;

    private final Set<String> scheduledChecks = new HashSet<>();
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;

    private static final long SCHEDULE_INTERVAL_IN_SECONDS = 60;

    @Autowired
    public IntegrationController(DataManager dataManager, EventBus eventBus, StatusChangeHandlerProvider handlerProvider) {
        this.dataManager = dataManager;
        this.eventBus = eventBus;
        this.draftHandlers = new HashMap<>();
        this.deployedHandlers = new HashMap<>();

        handlerProvider.getDraftStatusChangeHandlers().forEach(h ->
           draftHandlers.putAll(h.getTriggerStatuses()
               .stream()
               .collect(Collectors.toMap(t -> t, t -> h)))
        );

        handlerProvider.getDeployedStatusChangeHandlers().forEach(h ->
            deployedHandlers.putAll(h.getTriggerStatuses()
                .stream()
                .collect(Collectors.toMap(t -> t, i -> h)))
        );
    }

    @PostConstruct
    public void start() {
        executor = Executors.newSingleThreadExecutor();
        scheduler = Executors.newScheduledThreadPool(1);
        scanIntegrationsForWork();

        eventBus.subscribe("integration-controller", getChangeEventSubscription());
    }

    @PreDestroy
    public void stop() {
        eventBus.unsubscribe("integration-controller");
        scheduler.shutdownNow();
        executor.shutdownNow();
    }

    private EventBus.Subscription getChangeEventSubscription() {
        return (event, data) -> {
            // Never do anything that could block in this callback!
            if (event!=null && "change-event".equals(event)) {
                try {
                    ChangeEvent changeEvent = Json.mapper().readValue(data, ChangeEvent.class);
                    if (changeEvent != null) {
                        changeEvent.getId().ifPresent(id -> {
                            changeEvent.getKind()
                                       .map(Kind::from)
                                       .filter(k -> k == Kind.Integration)
                                       .ifPresent(k -> {
                                           checkIntegrationStatusIfNotAlreadyInProgress(id);
                                       });
                        });
                    }
                } catch (IOException e) {
                    LOG.error("Error while subscribing to change-event {}", data, e);
                }
            }
        };
    }

    private void checkIntegrationStatusIfNotAlreadyInProgress(String id) {
        executor.execute(() -> {
            Integration integration = dataManager.fetch(Integration.class, id);
            if( integration!=null ) {

                integration.getDraftRevision().ifPresent(r -> {
                    String scheduledKey = getIntegrationMarkerKey(integration, r);
                    // Don't start check is already a check is running
                    if (!scheduledChecks.contains(scheduledKey)) {
                        checkIntegrationStatus(integration);
                    }
                });

                integration.getDeployedRevision().ifPresent(r -> {
                    String scheduledKey = getIntegrationMarkerKey(integration, r);
                    // Don't start check is already a check is running
                    if (!scheduledChecks.contains(scheduledKey)) {
                        checkIntegrationStatus(integration);
                    }
                });

            }
        });
    }

    private void scanIntegrationsForWork() {
        executor.submit(() -> {
            dataManager.fetchAll(Integration.class).getItems().forEach(integration -> {
                LOG.info("Checking integrations for their status.");
                checkIntegrationStatus(integration);
            });
        });
    }

    private void checkIntegrationStatus(Integration integration) {
        if (integration == null) {
            return;
        }

       integration.getDraftRevision().ifPresent(
           r -> handleRevision(integration, r, draftHandlers)
       );

        integration.getDeployedRevision().ifPresent(
            r -> handleRevision(integration, r, deployedHandlers)
        );
    }


    private void handleRevision(Integration integration, IntegrationRevision revision, Map<IntegrationState, StatusChangeHandler> handlers) {
        IntegrationState target = revision.getDesiredState();
        IntegrationState current = revision.getCurrentState();

        if (revision.isPending()) {
            integration.getId().ifPresent(integrationId -> {
                StatusChangeHandler statusChangeHandler = handlers.get(target);
                if (statusChangeHandler != null) {
                    LOG.info("Integration {} : Target state \"{}\" != current state \"{}\" --> calling status change handler", integrationId, target.toString(), current);
                    callStatusChangeHandler(statusChangeHandler, integration, revision);
                }
            });
        } else {
            // When the desired state is reached remove the marker so that a next change trigger a check again
            // Doesn't harm when no such key exists
            scheduledChecks.remove(getIntegrationMarkerKey(integration, revision));
        }
    }

    private void callStatusChangeHandler(StatusChangeHandler handler, Integration integration, IntegrationRevision revision) {
        executor.submit(() -> {
            String checkKey = getIntegrationMarkerKey(integration, revision);
            scheduledChecks.add(checkKey);

            if (stale(handler, integration, revision)) {
                scheduledChecks.remove(checkKey);
                return;
            }

            try {
                LOG.info("Integration {} : Start processing integration with {}", integration.getId().orElse("[none]"), handler.getClass().getSimpleName());
                StatusUpdate update = handler.execute(integration, revision);
                if (update!=null) {
                    LOG.info("{} : Setting status to {}", getLabel(integration), update.getState());
                    updateRevisionStatus(integration, revision, update);

                }

            } catch (@SuppressWarnings("PMD.AvoidCatchingGenericException") Exception e) {
                LOG.error("Error while processing integration status for integration {}", integration.getId().orElse("[none]"), e);
                // Something went wrong.. lets note it.
                updateRevisionStatus(integration, revision, new StatusUpdate(revision.getVersion().orElse(0), IntegrationState.Error, e.getMessage()));
            } finally {
                // Add a next check for the next interval
                reschedule(integration.getId().get());
            }
        });
    }


    /**
     * Updates the {@link Integration} with the {@link StatusUpdate} returned by the {@link StatusChangeHandler}.
     * @param integration      The integration.
     * @param revision         The revision the update refers to.
     * @param update           The status update.
     */
    private void updateRevisionStatus(Integration integration, IntegrationRevision revision, StatusUpdate update) {
        Integration current = dataManager.fetch(Integration.class, integration.getId().get());

        //In most cases status updates are about deployed/older revisions, with just a few exceptions.
        // In all other cases we can safely discard the draft.
        boolean retainDraft = integration.getDraftRevision().isPresent()
            && (update.getState() == IntegrationState.Draft
                || update.getState() == IntegrationState.Pending
                || update.getState() == IntegrationState.Error);

        IntegrationRevision updatedDraft = retainDraft
            ? integration.getDraftRevision().get().withCurrentState(update.getState(), update.getStatusMessage())
            : null;

        //We need to iterate all revisions and update the status where there revision number matches.
        List<IntegrationRevision> updatedRevisions = integration.getRevisions().stream()
            .map(r -> r.getVersion().orElse(0).equals(revision.getVersion().orElse(0))
                ? r.withCurrentState(update.getState(), update.getStatusMessage())
                : r)
            .collect(Collectors.toList());

        dataManager.update(new Integration.Builder()
                .createFrom(current)
                .draftRevision(updatedDraft)
                .revisions(updatedRevisions)
                .stepsDone(Optional.ofNullable(update.getStepsPerformed()))
                .lastUpdated(new Date())
                .build());

    }

    private void reschedule(String integrationId) {
        scheduler.schedule(() -> {
            Integration i = dataManager.fetch(Integration.class, integrationId);
            checkIntegrationStatus(i);
        }, SCHEDULE_INTERVAL_IN_SECONDS, TimeUnit.SECONDS);
    }


    private static String getLabel(Integration integration) {
        return "Integration " + integration.getId().orElse("[none]");
    }

    private static String getIntegrationMarkerKey(Integration integration, IntegrationRevision revision) {
        return revision.getDesiredState().name() +
               ":" +
               integration.getId().orElseThrow(() -> new IllegalArgumentException("No id set in integration " + integration)) +
               ":" +
               revision.getVersion().orElseThrow(() -> new IllegalArgumentException("No version set in revision " + revision));
    }

    private static boolean stale(StatusChangeHandler handler, Integration integration, IntegrationRevision revision) {
        if (integration == null || handler == null) {
            return true;
        }

        IntegrationState targetState = revision.getDesiredState();

        return targetState.equals(revision.getCurrentState())
               || !handler.getTriggerStatuses().contains(targetState);
    }
}
