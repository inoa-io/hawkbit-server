package org.eclipse.hawkbit.dmf.hono;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.hawkbit.dmf.hono.model.DeviceSecret;
import org.eclipse.hawkbit.dmf.hono.model.HonoCredentials;
import org.eclipse.hawkbit.dmf.hono.model.HonoSecret;
import org.eclipse.hawkbit.dmf.hono.model.IdentifiableHonoDevice;
import org.eclipse.hawkbit.dmf.hono.model.IdentifiableHonoTenant;
import org.eclipse.hawkbit.im.authentication.PermissionService;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetTag;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
@RequiredArgsConstructor
public class HonoDeviceSync {

    private final EntityFactory entityFactory;

    private final SystemManagement systemManagement;

    private final SystemSecurityContext systemSecurityContext;

    private final TargetManagement targetManagement;

    private final TargetTagManagement targetTagManagement;

    private final PermissionService permissionService;

    private final HonoProperties honoProperties;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Semaphore> mutexes = new HashMap<>();
    private boolean syncedInitially = false;

    private String oidcAccessToken = null;
    private Instant oidcAccessTokenExpirationDate;

    @PostConstruct
    protected void init() {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initApplicationReady() {
        // permissionService.setHonoSyncEnabled(true);

        // Since ApplicationReadyEvent is emitted multiple times make sure it is synced at most once during startup.
        if (!syncedInitially) {
            syncedInitially = true;
            synchronize(false);
        }
    }

    public void synchronize(boolean syncOnlyCurrentTenant) {
        try {
            String currentTenant = null;
            if (syncOnlyCurrentTenant) {
                currentTenant = systemManagement.currentTenant();
            }

            List<IdentifiableHonoTenant> tenants = getAllHonoTenants();
            for (IdentifiableHonoTenant honoTenant : tenants) {
                String tenant = honoTenant.getId();

                if (syncOnlyCurrentTenant && !tenant.equals(currentTenant)) {
                    continue;
                }

                synchronizeTenant(tenant);
            }
        } catch (IOException e) {
            log.error("Could not parse hono api response.", e);
        } catch (InterruptedException e) {
            log.warn("Synchronizing hawkbit with Hono has been interrupted.", e);
        }
    }

    private void synchronizeTenant(String tenant) throws IOException, InterruptedException {
        Semaphore semaphore = mutexes.computeIfAbsent(tenant, t -> new Semaphore(1));
        semaphore.acquire();

        try {
            Map<String, IdentifiableHonoDevice> honoDevices = getAllHonoDevices(tenant);
            Slice<Target> targets = systemSecurityContext.runAsSystemAsTenant(
                    () -> targetManagement.findAll(Pageable.unpaged()), tenant);

            for (Target target : targets) {
                String controllerId = target.getControllerId();
                if (honoDevices.containsKey(controllerId)) {
                    IdentifiableHonoDevice honoDevice = honoDevices.remove(controllerId);
                    systemSecurityContext.runAsSystemAsTenant(() -> updateTarget(honoDevice), tenant);
                } else {
                    systemSecurityContext.runAsSystemAsTenant(() -> {
                        targetManagement.deleteByControllerID(target.getControllerId());
                        return true;
                    }, tenant);
                }
            }

            // At this point honoTargets only contains objects which were not found in hawkBit's target repository
            for (Map.Entry<String, IdentifiableHonoDevice> entry : honoDevices.entrySet()) {
                systemSecurityContext.runAsSystemAsTenant(() -> createTarget(entry.getValue(), tenant), tenant);
            }
        } finally {
            semaphore.release();
        }
    }

    public void checkDeviceIfAbsentSync(String tenant, String deviceID) {
        Optional<Target> target = systemSecurityContext.runAsSystemAsTenant(
                () -> targetManagement.getByControllerID(deviceID), tenant);
        if (!target.isPresent()) {
            try {
                synchronizeTenant(tenant);
            } catch (IOException | InterruptedException e) {
                log.error("Could not synchronize with hono for tenant {}.", tenant, e);
            }
        }
    }

    private List<IdentifiableHonoTenant> getAllHonoTenants() throws IOException {
        List<IdentifiableHonoTenant> tenants;
        long offset = 0;
        HttpURLConnection connection = getHonoData(
                honoProperties.getTenantListUri() + (honoProperties.getTenantListUri().contains("?") ? "&" : "?")
                        + "offset=" + offset, null);

        tenants = objectMapper
                .readValue(connection.getInputStream(), new TypeReference<List<IdentifiableHonoTenant>>() {

                });
        return tenants;
    }

    private Map<String, IdentifiableHonoDevice> getAllHonoDevices(String tenant) throws IOException {
        Map<String, IdentifiableHonoDevice> devices = new HashMap<>();
        long offset = 0;
        HttpURLConnection connection = getHonoData(honoProperties.getDeviceListUri().replace("$tenantId", tenant)
                + (honoProperties.getDeviceListUri().contains("?") ? "&" : "?") + "offset=" + offset, tenant);

        List<IdentifiableHonoDevice> page = objectMapper
                .readValue(connection.getInputStream(), new TypeReference<List<IdentifiableHonoDevice>>() {

                });
        for (IdentifiableHonoDevice identifiableHonoDevice : page) {
            devices.put(identifiableHonoDevice.getId(), identifiableHonoDevice);
        }
        return devices;
    }

    public DeviceSecret getAllHonoCredentials(String tenant, String deviceId) {
        try {
            HttpURLConnection connection = getHonoData(
                    honoProperties.getCredentialsListUri().replace("$tenantId", tenant)
                            .replace("$deviceId", deviceId), tenant);
            List<HonoCredentials> honoCredentials = objectMapper
                    .readValue(connection.getInputStream(), new TypeReference<List<HonoCredentials>>() {

                    });

            Optional<HonoCredentials> password = honoCredentials.stream()
                    .filter(i -> i.getType().equals("password")).findFirst();
            if (password.isPresent()) {
                HonoSecret remoteSecret = password.get().getSecrets().get(0);
                connection = getHonoData(
                        honoProperties.getCredentialsSecretListUri().replace("$tenantId", tenant)
                                .replace("$deviceId", deviceId)
                                .replace("$credentialId", password.get().getCredentialId())
                                .replace("$secretId", remoteSecret.getId()), tenant);

                HonoSecret secret = objectMapper
                        .readValue(connection.getInputStream(), HonoSecret.class);
                log.info("{}", secret);
                return new DeviceSecret(tenant, deviceId, secret);
            }
            return null;
        } catch (IOException e) {
            log.error("Could not read credentials for device '{}/{}'.", tenant, deviceId, e);
            return null;
        }
    }

    private HttpURLConnection getHonoData(String uri, String tenantId) throws IOException {
        URL url = new URL(uri);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        switch (honoProperties.getAuthenticationMethod()) {
        case "basic":
            connection.setRequestProperty("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(
                            (honoProperties.getUsername() + ":" + honoProperties.getPassword()).getBytes()));
            break;

        case "oidc":
            if (oidcAccessToken == null ||
                    (oidcAccessTokenExpirationDate != null && oidcAccessTokenExpirationDate.isBefore(Instant.now()))) {

                URL oidcTokenUrl = new URL(honoProperties.getOidcTokenUri());
                HttpURLConnection jwtConnection = (HttpURLConnection) oidcTokenUrl.openConnection();
                jwtConnection.setDoOutput(true);
                DataOutputStream outputStream = new DataOutputStream(jwtConnection.getOutputStream());
                if (honoProperties.getOidcClientSecret() != null && !honoProperties.getOidcClientSecret().isEmpty()) {
                    outputStream.writeBytes("grant_type=client_credentials"
                            + "&client_id=" + URLEncoder.encode(honoProperties.getOidcClientId(), "UTF-8")
                            + "&client_secret=" + URLEncoder.encode(honoProperties.getOidcClientSecret(), "UTF-8"));
                } else {
                    outputStream.writeBytes("grant_type=password"
                            + "&client_id=" + URLEncoder.encode(honoProperties.getOidcClientId(), "UTF-8")
                            + "&username=" + URLEncoder.encode(honoProperties.getUsername(), "UTF-8")
                            + "&password=" + URLEncoder.encode(honoProperties.getPassword(), "UTF-8"));
                }
                outputStream.flush();
                outputStream.close();

                int statusCode = jwtConnection.getResponseCode();
                if (statusCode >= 200 && statusCode < 300) {
                    JsonNode node = objectMapper.readValue(jwtConnection.getInputStream(), JsonNode.class);
                    oidcAccessToken = node.get("access_token").asText();
                    JsonNode expiresIn = node.get("expires_in");
                    if (expiresIn != null) {
                        oidcAccessTokenExpirationDate = Instant.now().plusSeconds(expiresIn.asLong());
                    }
                } else {
                    throw new IOException(
                            "Server returned HTTP response code: " + statusCode + " for URL: " + oidcTokenUrl
                                    .toString());
                }
            }
            connection.setRequestProperty("Authorization", "Bearer " + oidcAccessToken);
            if (tenantId != null) {
                connection.setRequestProperty("x-inoa-tenant", tenantId);
            }
            break;
        }

        return connection;
    }

    private Target createTarget(IdentifiableHonoDevice honoDevice, String tenant) {
        systemManagement.getTenantMetadata(tenant);
        Target target = targetManagement.create(entityFactory.target().create()
                .controllerId(honoDevice.getId())
                .name(getDeviceName(honoDevice)));
        syncTags(target, getDeviceTags(honoDevice));
        return target;
    }

    private Target updateTarget(IdentifiableHonoDevice honoDevice) {
        Target target = targetManagement.update(entityFactory.target()
                .update(honoDevice.getId())
                .name(getDeviceName(honoDevice)));
        syncTags(target, getDeviceTags(honoDevice));
        return target;
    }

    private void syncTags(Target target, @Nullable String tags) {
        String controllerId = target.getControllerId();
        Collection<String> tagNames;
        if (tags != null) {
            tagNames = new ArrayList<>(Arrays.asList(tags.split(",")));
        } else {
            tagNames = Collections.emptyList();
        }

        Slice<TargetTag> assignedTags = targetTagManagement.findByTarget(Pageable.unpaged(), target.getControllerId());
        for (TargetTag tag : assignedTags) {
            if (tagNames.contains(tag.getName())) {
                tagNames.remove(tag.getName());
            } else {
                targetManagement.unAssignTag(controllerId, tag.getId());
            }
        }

        for (String name : tagNames) {
            TargetTag tag = targetTagManagement.getByName(name).orElseGet(
                    () -> targetTagManagement.create(entityFactory.tag().create().name(name)));

            targetManagement.assignTag(Collections.singleton(target.getControllerId()), tag.getId());
        }
    }

    private String getDeviceName(IdentifiableHonoDevice honoDevice) {
        return honoDevice.getName();
    }

    private String getDeviceTags(IdentifiableHonoDevice honoDevice) {
/*        Object ext = honoDevice.getDevice().getExt();
        if (ext instanceof JsonNode) {
            JsonNode tagsValue = ((JsonNode) ext).get("TargetTags");
            if (tagsValue != null) {
                return tagsValue.asText();
            }
        } else {
            LOG.warn("The extension field of device '{}/{}' is not a valid JSON object.", honoDevice.getTenant(),
                    honoDevice.getId());
        }*/

        return null;
    }
}
