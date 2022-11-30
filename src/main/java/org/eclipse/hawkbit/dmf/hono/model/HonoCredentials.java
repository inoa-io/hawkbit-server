package org.eclipse.hawkbit.dmf.hono.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HonoCredentials {

    @JsonProperty("credential_id")
    private String credentialId;
    private String type;
    private byte[] value;

}
