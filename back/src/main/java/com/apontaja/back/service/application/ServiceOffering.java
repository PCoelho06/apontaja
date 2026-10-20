package com.apontaja.back.service.application;

import java.util.List;

/** Une prestation et les ressources vivantes du salon capables de la réaliser (ordre des ressources). */
public record ServiceOffering(ServiceSummary service, List<ResourceTerms> resources) {

    public ServiceOffering {
        resources = List.copyOf(resources);
    }
}
