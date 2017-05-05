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
package io.syndesis.github;

import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * Github specific properties, set it in application.yml with a prefix "github."
 */
@ConfigurationProperties("github")
public class GitHubProperties {

    private String service = "ipaas-github-proxy";

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }
}