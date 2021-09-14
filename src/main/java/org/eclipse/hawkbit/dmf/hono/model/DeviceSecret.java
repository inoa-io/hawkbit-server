package org.eclipse.hawkbit.dmf.hono.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSecret {

    private String tenantId;
    private String deviceId;
    private HonoSecret secret;

}
