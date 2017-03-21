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
package com.redhat.ipaas.rest.v1.handler.tests;

import com.redhat.ipaas.dao.init.ModelData;
import com.redhat.ipaas.dao.manager.DataManager;
import com.redhat.ipaas.jsondb.JsonDB;
import com.redhat.ipaas.model.Kind;
import com.redhat.ipaas.model.ListResult;
import com.redhat.ipaas.model.WithId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;

@Path("/test-support")
@org.springframework.stereotype.Component
@ConditionalOnProperty(value = "endpoints.test_support.enabled")
public class TestSupportHandler {

    private final DataManager dataMgr;
    private final JsonDB jsondb;

    private final Kind DB_ENTITIES[] = new Kind[]{
        Kind.Connection,
        Kind.Connector,
        Kind.Integration,
        Kind.User,
        Kind.ConnectorGroup
    };

    public TestSupportHandler(DataManager dataMgr, JsonDB jsondb) {
        this.dataMgr = dataMgr;
        this.jsondb = jsondb;
    }

    @GET
    @Path("/reset-db")
    public void resetDBToDefault() {
        jsondb.set("/", "{}");
        dataMgr.clearCache();
        dataMgr.init();
    }

    @POST
    @Path("/restore-db")
    @Consumes(MediaType.APPLICATION_JSON)
    public void restoreDB(ModelData[] data) {
        for (Kind kind : DB_ENTITIES) {
            ListResult<? extends WithId> l = dataMgr.fetchAll(kind.getModelClass(), (x) -> x);
            for (WithId entity : l.getItems()) {
                dataMgr.delete(kind.getModelClass(), (String) entity.getId().get());
            }
        }
        for (ModelData modelData : data) {
            dataMgr.store(modelData);
        }
    }

    @GET
    @Path("/snapshot-db")
    @Produces(MediaType.APPLICATION_JSON)
    public ArrayList<ModelData> snapshotDB() {
        ArrayList<ModelData> result = new ArrayList<ModelData>();
        for (Kind kind : DB_ENTITIES) {
            ListResult<? extends WithId> l = dataMgr.fetchAll(kind.getModelClass(), (x) -> x);
            for (WithId entity : l.getItems()) {
                result.add(new ModelData(kind, entity));
            }
        }
        return result;
    }

}
