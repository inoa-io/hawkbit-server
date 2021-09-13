package org.eclipse.hawkbit.dmf.hono.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HonoCredentials {

    @JsonProperty("credential_id")
    private String credentialId;
    @JsonProperty("auth_id")
    private String authId;
    private String type;
    private List<HonoSecret> secrets = new ArrayList<>();

}
