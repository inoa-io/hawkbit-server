package io.inoa.cloud.hawkbit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = { "hawkbit.dmf.rabbitmq.enabled=false" })
public class ApplicationTest {

    @Test
    public void contextLoad() {
    }
}
