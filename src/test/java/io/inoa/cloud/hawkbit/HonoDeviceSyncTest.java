package io.inoa.cloud.hawkbit;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.hawkbit.dmf.hono.HonoDeviceSync;
import org.eclipse.hawkbit.dmf.hono.HonoProperties;
import org.eclipse.hawkbit.dmf.hono.model.DeviceSecret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@SpringBootTest(properties = {
        "hawkbit.dmf.rabbitmq.enabled=false" }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class HonoDeviceSyncTest {

    WireMockServer wireMockServer;

    @Autowired
    private HonoDeviceSync honoDeviceSync;
    @Autowired
    private HonoProperties honoProperties;

    @Autowired TestRestTemplate testRestTemplate;

    @BeforeEach
    public void setup() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        // wireMockServer.stubFor()

        wireMockServer.stubFor(get(urlEqualTo("/api/gateway-registry/tenants?offset=0")).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBodyFile("json/tenants.json")));

        wireMockServer.stubFor(get(urlEqualTo("/api/gateway-registry/gateways?offset=0")).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBodyFile("json/gateways.json")));

        wireMockServer.stubFor(
                get(urlEqualTo("/api/gateway-registry/gateways/4437e4ea-1548-11ec-a507-bb528caa9277/credentials"))
                        .willReturn(
                                aResponse().withHeader("Content-Type", "application/json")
                                        .withBodyFile("json/credentials.json")));

        wireMockServer.stubFor(get(urlEqualTo(
                "/api/gateway-registry/gateways/4437e4ea-1548-11ec-a507-bb528caa9277/credentials/2f92d064-04e4-11ec"
                        + "-a884-f3c99a940904/secrets"
                        + "/b1f16184-04cf-11ec-9955-b780b05b0a67"))
                .willReturn(
                        aResponse().withHeader("Content-Type", "application/json").withBodyFile("json/secret.json")));

        wireMockServer.stubFor(post(urlEqualTo("/auth/realms/inoa/protocol/openid-connect/token"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"access_token\": "
                        +
                        "\"eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJfUjZJWXNHdGdad2dkMDBMdHFRVnRBYlRmOVJTLWxGNUtJUVNBalBGcjVnIn0.eyJqdGkiOiI2N2QyMmQ0My0zMjQ2LTRhYWMtOWEwNi1lZDNlZWVkYmNkYmUiLCJleHAiOjE1Mjg3OTczNzgsIm5iZiI6MCwiaWF0IjoxNTI4Nzk3MDc4LCJpc3MiOiJodHRwczovL3Rlc3Qtbmc6ODg0My9hdXRoL3JlYWxtcy9kY200Y2hlIiwiYXVkIjoiY3VybCIsInN1YiI6ImVmNjRlYjRlLTBhNmQtNGYxMy1iZjQ0LWI2YjQxZWI5YTQ1ZiIsInR5cCI6IkJlYXJlciIsImF6cCI6ImN1cmwiLCJhdXRoX3RpbWUiOjAsInNlc3Npb25fc3RhdGUiOiIyMzNkNDAyYy05YmIwLTRjM2YtOGNmMy1mYjAwNTFjMjNhNGYiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbInVtYV9hdXRob3JpemF0aW9uIiwidXNlciJdfSwicmVzb3VyY2VfYWNjZXNzIjp7ImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sImNsaWVudEhvc3QiOiIxOTIuMTY4LjIuMTc4IiwiY2xpZW50SWQiOiJjdXJsIiwicHJlZmVycmVkX3VzZXJuYW1lIjoic2VydmljZS1hY2NvdW50LWN1cmwiLCJjbGllbnRBZGRyZXNzIjoiMTkyLjE2OC4yLjE3OCIsImVtYWlsIjoic2VydmljZS1hY2NvdW50LWN1cmxAcGxhY2Vob2xkZXIub3JnIn0.BdtmOKJNi-wzFBy-FUZu6_zRlukU81-yoXGl4YomEXMTLkK4AaUIsBO2Y3LjWt5vDbrki6RXZXNFbTEkDJMsMKXzur_xxAq5PzNE6q0QyEaTttsfrVETuzZMsU9r5Z0dfVSMIdAnpG7qgWMzETj2E9tOuZN1Mn7X8JRl6qQC0RLvl_TZcuRLElHoZbpvs2HiVRYkIhiG9Gn89cc6LT02wXdeGMccNx4jEyCY_YKhKsT6QNfzKmAKtiYdSF_arhlF6rlIf_HcCDjUIkgSQ_bY0LF5tA6FvEM2stCjO2YPjeVU2WrmQOYJyQ1FyvswiGBx2tutE-yLYdEmYwJknF2JuQ\", \"expires_in\": 120, \"token_type\": \"bearer\"}")));
        wireMockServer.start();
        honoProperties.setTenantListUri(
                String.format("http://localhost:%d/api/gateway-registry/tenants", wireMockServer.port()));
        honoProperties.setDeviceListUri(
                String.format("http://localhost:%d/api/gateway-registry/gateways", wireMockServer.port()));
        honoProperties.setOidcTokenUri(String
                .format("http://localhost:%d/auth/realms/inoa/protocol/openid-connect/token", wireMockServer.port()));
        honoProperties.setCredentialsListUri(String
                .format("http://localhost:%d/api/gateway-registry/gateways/$deviceId/credentials",
                        wireMockServer.port()));
        honoProperties.setCredentialsSecretListUri(String
                .format("http://localhost:%d/api/gateway-registry/gateways/$deviceId/credentials/$credentialId"
                                + "/secrets/$secretId",
                        wireMockServer.port()));

    }

    @AfterEach
    public void teardown() {
        wireMockServer.stop();
    }

    @Test
    public void testReadCredentialsWork() {
        assertNotNull(honoDeviceSync);
        honoDeviceSync.synchronize(false);
        DeviceSecret allHonoCredentials = honoDeviceSync
                .getAllHonoCredentials("tet", "4437e4ea-1548-11ec-a507-bb528caa9277");
        assertNotNull(allHonoCredentials);
    }

    @Test
    public void testDeviceAuthenticationAfterSync() {
        assertNotNull(honoDeviceSync);
        honoDeviceSync.synchronize(false);

        final HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "HonoToken test");
        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<JsonNode> exchange = testRestTemplate.getRestTemplate()
                .exchange("/29060e54-1548-11ec-9f0d-77b427b9fcee/controller/v1/4437e4ea-1548-11ec-a507-bb528caa9277",
                        HttpMethod.GET, entity,
                        JsonNode.class);
        assertEquals(200, exchange.getStatusCodeValue());

    }

    @Test
    public void testDeviceAuthenticationAfterSyncAndReadableTenant() {
        assertNotNull(honoDeviceSync);
        honoDeviceSync.synchronize(false);

        final HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "HonoToken test");
        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<JsonNode> exchange = testRestTemplate.getRestTemplate()
                .exchange("/inoa/controller/v1/4437e4ea-1548-11ec-a507-bb528caa9277",
                        HttpMethod.GET, entity,
                        JsonNode.class);
        assertEquals(200, exchange.getStatusCodeValue(), exchange.getBody().toString());

    }
}
