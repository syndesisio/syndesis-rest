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
package com.redhat.ipaas.rest.v1.dao;

import java.util.*;
import java.util.stream.Collectors;

import javax.inject.Named;

import com.redhat.ipaas.rest.v1.model.integration.Integration;
import com.redhat.ipaas.rest.v1.model.ListResult;
import com.redhat.ipaas.rest.v1.model.WithId;
import com.redhat.ipaas.rest.v1.controller.handler.exception.IPaasServerException;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Named(Integration.KIND)
@Service
public class IntegrationDAO implements DataAccessObject<Integration> {

    //The configuration key
    public static final String CONFIGURATION_KEY = "configuration";

    private KubernetesClient kubernetesClient;

    @Autowired
    public IntegrationDAO(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public Class<Integration> getType() {
        return Integration.class;
    }

    @Override
    public Integration fetch(String id) {
        return toIntegration(kubernetesClient.configMaps().withName(id).get());
    }

    @Override
    public ListResult<Integration> fetchAll() {
        ConfigMapList list = kubernetesClient.configMaps().withLabel(Integration.Label.NAME.value()).list();
        List<ConfigMap> maps = list != null ? list.getItems() : Collections.emptyList();
        List<Integration> integrations = maps.stream().map(c -> toIntegration(c)).collect(Collectors.toList());
        return new ListResult.Builder<Integration>()
            .items(integrations)
            .totalCount(integrations.size())
            .build();
    }

    @Override
    public Integration create(Integration entity) {
        return toIntegration(kubernetesClient.configMaps().create(toConfigMap(entity)));
    }

    @Override
    public Integration update(Integration entity) {
        String id = entity.getId().orElseThrow(() -> new IllegalArgumentException("Update integration requires an entity with an id."));
        ConfigMap old = kubernetesClient.configMaps().withName(id).get();
        if (old != null) {
            ConfigMap updated = kubernetesClient.configMaps().withName(id).replace(toConfigMap(entity));
            return toIntegration(old);
        } else {
            return null;
        }
    }

    @Override
    public boolean delete(WithId<Integration> entity) {
        try {
            return delete(entity.getId().orElseThrow(() -> new IllegalArgumentException("Delete integration requires an entity with an id.")));
        } catch (Throwable t) {
            throw IPaasServerException.launderThrowable(t);
        }
    }

    @Override
    public boolean delete(String id) {
        return kubernetesClient.configMaps().withName(id).delete();
    }

    private static boolean containsIntegration(ConfigMap configMap) {
        return configMap != null &&
               configMap.getMetadata().getLabels().containsKey(Integration.Label.NAME.value());
    }

    private static Integration toIntegration(ConfigMap configMap) {
        if (configMap == null) {
            throw new IllegalArgumentException("ConfigMap cannot be null.");
        }
        if (configMap.getMetadata() == null) {
            throw new IllegalArgumentException("ConfigMap metadata cannot be null.");
        }
        if (configMap.getMetadata().getName() == null || configMap.getMetadata().getName().isEmpty()) {
            throw new IllegalArgumentException("ConfigMap name cannot be blank.");
        }
        if (!containsIntegration(configMap)) {
            throw new IllegalArgumentException("ConfigMap [" + configMap.getMetadata().getName() + "] does not contain an Integration.");
        }

        String id = configMap.getMetadata().getName();
        String name = getLabel(configMap, Integration.Label.NAME);
        String templateId = getLabel(configMap, Integration.Label.TEMPLATE);
        Map<String, String> data = configMap.getData();
        if (data == null) {
            throw new IllegalStateException("ConfigMap [" + id + "] must not be empty.");
        }
        String configuration = data.get(CONFIGURATION_KEY);
        if (configuration == null) {
            throw new IllegalStateException("ConfigMap [" + id + "] must contain an entry " + CONFIGURATION_KEY + ".");
        }
        return new Integration.Builder().name(name)
            .id(Optional.of(id))
            .integrationTemplateId(Optional.ofNullable(templateId))
            .configuration(configuration)
            .build();
    }

    private static String getLabel(ConfigMap configMap, Integration.Label label) {
        return configMap.getMetadata().getLabels().get(label.value());
    }

    private static final ConfigMap toConfigMap(Integration integration) {
        String id = integration.getId().orElseThrow(() -> new IllegalArgumentException("Integration requires an id."));
        String name = integration.getName();
        String templateId = integration.getIntegrationTemplateId().orElse(null);
        String configuration = integration.getConfiguration().orElse(null);

        ConfigMapBuilder builder = new ConfigMapBuilder()
            .withNewMetadata()
                .withName(id)
                .addToLabels(Integration.Label.ID.value(), id)
                .addToLabels(Integration.Label.NAME.value(), name)
            .endMetadata();
        if (templateId != null) {
            builder.editMetadata().addToLabels(Integration.Label.TEMPLATE.value(), templateId);
        }

        if (configuration != null) {
            builder.addToData(CONFIGURATION_KEY, configuration);
        }
        return builder.build();
    }

}
