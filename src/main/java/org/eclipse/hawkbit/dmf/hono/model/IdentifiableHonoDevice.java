package org.eclipse.hawkbit.dmf.hono.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IdentifiableHonoDevice {

    @JsonProperty("gateway_id")
    private String id;
    private String name;
}
