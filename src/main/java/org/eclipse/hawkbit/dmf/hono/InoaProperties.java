package org.eclipse.hawkbit.dmf.hono;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("inoa")
public class InoaProperties {

    private String defaultTenant = "DEFAULT";
}
