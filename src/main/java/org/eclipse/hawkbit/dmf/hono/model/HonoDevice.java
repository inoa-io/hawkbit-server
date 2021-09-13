package org.eclipse.hawkbit.dmf.hono.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class HonoDevice {

    private boolean enabled;
    private JsonNode ext;

    @JsonProperty("enabled")
    public boolean isEnabled() {
        return enabled;
    }

    @JsonProperty("ext")
    public Object getExt() {
        return ext;
    }

    @JsonProperty("enabled")
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @JsonProperty("ext")
    public void setExt(JsonNode ext) {
        this.ext = ext;
    }
}

