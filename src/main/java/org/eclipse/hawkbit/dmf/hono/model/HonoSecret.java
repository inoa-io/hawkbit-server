package org.eclipse.hawkbit.dmf.hono.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HonoSecret {

    @JsonProperty("secret_id")
    private String id;
    private byte[] password;
}
