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
package com.redhat.ipaas.rest.v1.handler.connection;

import com.redhat.ipaas.dao.manager.DataManager;
import com.redhat.ipaas.model.Kind;
import com.redhat.ipaas.model.ValueResult;
import com.redhat.ipaas.model.connection.Connector;
import com.redhat.ipaas.project.converter.ProjectGenerator;
import com.redhat.ipaas.rest.v1.handler.BaseHandler;
import com.redhat.ipaas.rest.v1.operations.Getter;
import com.redhat.ipaas.rest.v1.operations.Lister;
import io.swagger.annotations.Api;
import org.springframework.util.FileSystemUtils;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Properties;

@Path("/connectors")
@Api(value = "connectors")
@org.springframework.stereotype.Component
public class ConnectorHandler extends BaseHandler implements Lister<Connector>, Getter<Connector> {

    private final ProjectGenerator projectGenerator;
    private String localMavenRepoLocation = null; // "/tmp/ipaas-local-mvn-repo";

    public ConnectorHandler(DataManager dataMgr, ProjectGenerator projectGenerator) {
        super(dataMgr);
        this.projectGenerator = projectGenerator;
    }

    @Override
    public Kind resourceKind() {
        return Kind.Connector;
    }

    @Path("/{id}/actions")
    public ConnectorActionHandler getActions(@PathParam("id") String connectorId) {
        return new ConnectorActionHandler(getDataManager(), connectorId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}/verify")
    public ValueResult<String> verify(@PathParam("id") String connectorId, Properties configuredProperties) throws IOException {
        Connector connector = get(connectorId);
        if (!connector.getCamelConnectorGAV().isPresent() ||
            !connector.getCamelConnectorPrefix().isPresent() ) {
            return ValueResult.success("unsupported");
        }

        String value = null;
        try {

            // we could cache the connectorClasspath.
            String connectorClasspath = getConnectorClasspath(connector);

            // shell out to java to validate the properties.
            Properties result = runValidator(connectorClasspath, connector.getCamelConnectorPrefix().get(), configuredProperties);
            value = result.getProperty("value");
            if (value == null) {
                return ValueResult.error("Invalid verifier result");
            }
            if ("error".equals(value)) {
                return ValueResult.error(result.getProperty("error"));
            }
        } catch (IOException|InterruptedException e) {
            ValueResult.error(e.toString());
        }
        return ValueResult.success(value);
    }

    private Properties runValidator(String classpath, String camelPrefix, Properties request) throws IOException, InterruptedException {
        Process java = new ProcessBuilder()
            .command(
                "java", "-classpath", classpath,
                "com.redhat.ipaas.connector.ConnectorVerifier", camelPrefix
            )
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();

        try (OutputStream os = java.getOutputStream()) {
            request.store(os, null);
        }
        Properties result = new Properties();
        try (InputStream is = java.getInputStream()) {
            result.load(is);
        }

        if (java.waitFor() != 0) {
            throw new IOException("Verifier failed with exit code: " + java.exitValue());
        }
        return result;
    }


    private String getConnectorClasspath(Connector connector) throws IOException, InterruptedException {
        byte[] pom = projectGenerator.generatePom(connector);
        java.nio.file.Path tmpDir = Files.createTempDirectory("ipaas-connector");
        try {
            Files.write(tmpDir.resolve("pom.xml"), pom);
            ArrayList<String> args = new ArrayList<>();
            args.add("mvn");
            args.add("org.apache.maven.plugins:maven-dependency-plugin:3.0.0:build-classpath");
            if (localMavenRepoLocation != null) {
                args.add("-Dmaven.repo.local=" + localMavenRepoLocation);
            }
            Process mvn = new ProcessBuilder().command(args)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .directory(tmpDir.toFile())
                .start();
            try {
                String result = parseClasspath(mvn.getInputStream());
                if (mvn.waitFor() != 0) {
                    throw new IOException("Could not get the connector classpath, mvn exit value: " + mvn.exitValue());
                }
                return result;
            } finally {
                mvn.getInputStream().close();
                mvn.getOutputStream().close();
            }
        } finally {
            FileSystemUtils.deleteRecursively(tmpDir.toFile());
        }
    }

    private String parseClasspath(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        boolean useNextLine = true;
        String result = null;
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("mvn: "+line);
            if (useNextLine) {
                useNextLine = false;
                result = line;
            }
            if (line.startsWith("[INFO] Dependencies classpath:")) {
                useNextLine = true;
            }
        }
        return result;
    }
}
