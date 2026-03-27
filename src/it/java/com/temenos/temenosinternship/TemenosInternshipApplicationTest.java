package com.temenos.temenosinternship;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TemenosInternshipApplicationTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:latest")
            .withExposedPorts(6379);

    static WireMockServer wireMockServer;

    private RestTemplate restTemplate = new RestTemplate();


    private int port;

    public TemenosInternshipApplicationTest(@LocalServerPort int port) {
        this.port = port;
    }

    @DynamicPropertySource
    public static void injectProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);

        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + postgresContainer.getHost() + ":" + postgresContainer.getFirstMappedPort() + "/" + postgresContainer.getDatabaseName());
        registry.add("spring.r2dbc.username", postgresContainer::getUsername);
        registry.add("spring.r2dbc.password", postgresContainer::getPassword);

        registry.add("spring.flyway.url", postgresContainer::getJdbcUrl);

        registry.add("spring.flyway.user", postgresContainer::getUsername);
        registry.add("spring.flyway.password", postgresContainer::getPassword);

        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @BeforeEach
    public void resetWireMockServer() {
        wireMockServer.resetAll();
    }

    @BeforeAll
    public static void init() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();

        WireMock.configureFor("localhost", 8089);
    }

    @AfterAll
    public static void stopContainers() {
        postgresContainer.stop();
        redisContainer.stop();
        wireMockServer.stop();
    }

    @Test
    public void testCreateTimer() {
        WireMock.stubFor(WireMock.get(WireMock.urlPathMatching("^" + "/callback"))
                .willReturn(WireMock.aResponse().withStatus(200)));


        var response = sendHttpMethod();
        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());

        // wait...
        Awaitility.await().timeout(15, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).until(() -> true);

        WireMock.verify(
                1,
                WireMock.getRequestedFor(WireMock.urlEqualTo("/callback"))
        );
    }

    private ResponseEntity<String> sendHttpMethod() {
        Map<String, String> body = Map.of("created", String.valueOf(System.currentTimeMillis()), "delay", String.valueOf(5), "callbackUrl", "http://localhost:8089/callback", "csrfToken", "12345");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        return restTemplate.postForEntity("http://localhost:" + port + "/api/timers", entity, String.class);
    }

}

