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
package io.syndesis.rest.v1.handler.integration;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import javax.validation.groups.ConvertGroup;
import javax.validation.groups.Default;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import io.syndesis.core.Tokens;
import io.syndesis.dao.manager.DataManager;
import io.syndesis.inspector.ClassInspector;
import io.syndesis.model.Kind;
import io.syndesis.model.connection.DataShape;
import io.syndesis.model.connection.DataShapeKinds;
import io.syndesis.model.filter.FilterOptions;
import io.syndesis.model.filter.Op;
import io.syndesis.model.integration.Integration;
import io.syndesis.model.integration.Step;
import io.syndesis.model.validation.AllValidations;
import io.syndesis.model.integration.IntegrationRevision;
import io.syndesis.model.integration.IntegrationState;
import io.syndesis.rest.v1.handler.BaseHandler;
import io.syndesis.rest.v1.operations.Creator;
import io.syndesis.rest.v1.operations.Deleter;
import io.syndesis.rest.v1.operations.Getter;
import io.syndesis.rest.v1.operations.Lister;
import io.syndesis.rest.v1.operations.Updater;
import io.syndesis.rest.v1.operations.Validating;
import org.springframework.stereotype.Component;

@Path("/integrations")
@Api(value = "integrations")
@Component
public class IntegrationHandler extends BaseHandler
    implements Lister<Integration>, Getter<Integration>, Creator<Integration>, Deleter<Integration>, Updater<Integration>, Validating<Integration> {

    private final ClassInspector classInspector;

    private final Validator validator;

    public IntegrationHandler(final DataManager dataMgr, final Validator validator, final ClassInspector classInspector) {
        super(dataMgr);
        this.validator = validator;
        this.classInspector = classInspector;
    }

    @Override
    public Kind resourceKind() {
        return Kind.Integration;
    }

    @Override
    public Integration get(String id) {
        Integration integration = Getter.super.get(id);

        //fudging the timesUsed for now
        Optional<IntegrationState> currnetState = integration.getDeployedRevision().map(r -> r.getCurrentState());
        if (currnetState.isPresent() && currnetState.get() == IntegrationState.Active) {
            return new Integration.Builder()
                    .createFrom(integration)
                    .timesUsed(BigInteger.valueOf(new Date().getTime()/1000000))
                    .build();
        }

        return integration;
    }

    @Override
    public Integration create(@ConvertGroup(from = Default.class, to = AllValidations.class) final Integration integration) {
        Date rightNow = new Date();

        Integration updatedIntegration = new Integration.Builder()
            .createFrom(integration)
            .token(Tokens.getAuthenticationToken())
            .lastUpdated(rightNow)
            .createdDate(rightNow)
            .build();

        return Creator.super.create(updatedIntegration);
    }

    @Override
    public void update(String id, @ConvertGroup(from = Default.class, to = AllValidations.class) Integration integration) {
        Integration updatedIntegration = new Integration.Builder()
            .createFrom(integration)
            .token(Tokens.getAuthenticationToken())
            .lastUpdated(new Date())
            .build();

        Updater.super.update(id, updatedIntegration);
    }


    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/{integrationId}/{revisionNumber}/filters/options")
    public FilterOptions getFilterOptions(@NotNull @PathParam("integrationId") @ApiParam(required = true) String id,
                                          @NotNull @PathParam("revisionNumber") @ApiParam(required = true)int revisionNumber,
                                          @DefaultValue("-1") @QueryParam("position") int position) {
        FilterOptions.Builder builder = new FilterOptions.Builder().addOp(Op.DEFAULT_OPTS);

        Integration integration = Getter.super.get(id);

        IntegrationRevision revision = integration.getRevisions().stream()
            .filter(r -> r.getVersion().isPresent() && r.getVersion().orElse(0) == revisionNumber)
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Could not find revision: [" + revisionNumber + "] for integration with id:[" + id + "]."));

        extractLastOutputDataShapeBeforePosition(revision, position).ifPresent(
            dataShape -> {
                String kind = dataShape.getKind();
                if (kind != null && kind.equals(DataShapeKinds.JAVA)) {
                    String type = dataShape.getType();
                    builder.paths(classInspector.getPaths(type));
                }
            });

        return builder.build();
    }

    // position == -1 --> last output datashape in the whole integration
    private Optional<DataShape> extractLastOutputDataShapeBeforePosition(IntegrationRevision revision,  int position) {
        List<? extends Step> steps = revision.getSpec().getSteps().orElse(Collections.emptyList());
        Optional<DataShape> lastOutputShape = Optional.empty();
        for (int i = 0; i < steps.size(); i++) {
            if (position != -1 && position == i) {
                return lastOutputShape;
            }
            Step step = steps.get(i);
            if (step.getAction().isPresent()) {
                DataShape shape = step.getAction().get().getOutputDataShape();
                if (shape != null && DataShapeKinds.JAVA.equals(shape.getKind())) {
                    lastOutputShape = Optional.of(shape);
                }
            }
        }
        return lastOutputShape;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/filters/options")
    public FilterOptions getGlobalFilterOptions() {
        return new FilterOptions.Builder().addOp(Op.DEFAULT_OPTS).build();
    }

    // Determine the current status to 'pending' or 'draft' immediately depending on
    // the desired stated. This status will be later changed by the activation handlers.
    // This is not the best place to set but should be done by the IntegrationController
    // However because of how the Controller works (i.e. that any change to the integration
    // within the controller will trigger an event again), the initial status must be set
    // from the outside for the moment.
    private IntegrationState determineCurrentStatus(IntegrationRevision revision) {
        IntegrationState desiredStatus = revision.getTargetState();
        return desiredStatus == IntegrationState.Draft ?
            IntegrationState.Draft :
            IntegrationState.Pending;
    }

    @Override
    public Validator getValidator() {
        return validator;
    }
}
