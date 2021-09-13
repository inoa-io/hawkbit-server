package org.eclipse.hawkbit.dmf.hono.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IdentifiableHonoTenant {

    @JsonProperty("tenant_id")
    private String id;
    private String name;
}
