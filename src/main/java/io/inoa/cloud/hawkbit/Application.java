package io.inoa.cloud.hawkbit;

import org.eclipse.hawkbit.autoconfigure.security.SecurityManagedConfiguration;
import org.eclipse.hawkbit.dmf.hono.DmfHonoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({SecurityManagedConfiguration.class, DmfHonoConfiguration.class})
// Exception squid:S1118 - Spring boot standard behavior
@SuppressWarnings({"squid:S1118"})
public class Application {

    /**
     * Main method to start the spring-boot application.
     *
     * @param args the VM arguments.
     */
    // Exception squid:S2095 - Spring boot standard behavior
    @SuppressWarnings({"squid:S2095"})
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
