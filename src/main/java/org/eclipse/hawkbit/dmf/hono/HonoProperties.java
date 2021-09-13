package org.eclipse.hawkbit.dmf.hono;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("hawkbit.dmf.hono")
public class HonoProperties {

    private String tenantListUri;
    private String deviceListUri;
    private String credentialsListUri;
    private String credentialsSecretListUri;
    private String authenticationMethod = "none";
    private String oidcTokenUri = "";
    private String oidcClientId = "";
    private String oidcClientSecret = "";
    private String username = "";
    private String password = "";
    private String targetNameField = "";
}
