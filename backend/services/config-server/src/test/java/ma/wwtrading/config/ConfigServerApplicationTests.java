package ma.wwtrading.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "eureka.client.enabled=false")
class ConfigServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
