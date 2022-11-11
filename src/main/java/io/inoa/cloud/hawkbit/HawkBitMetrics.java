package io.inoa.cloud.hawkbit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.FilterParams;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.jpa.DistributionSetRepository;
import org.eclipse.hawkbit.repository.jpa.TenantMetaDataRepository;
import org.eclipse.hawkbit.repository.jpa.model.JpaTenantMetaData;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class registers metrics, primarily used by prometheus.
 * <p>
 * Metrics registered:
 * - hawkbit_distribution_sets_total
 * - hawkbit_rollouts_total
 * - hawkbit_software_modules_total
 * - hawkbit_targets_total
 * - hawkbit_targets_in_time
 * - hawkbit_targets_overdue
 * - hawkbit_targets_error
 * - hawkbit_targets_in_sync
 * - hawkbit_targets_pending
 * - hawkbit_targets_registered
 * - hawkbit_targets_unknown
 * - hawkbit_target_installed_sm meta data about installed software modules foreach target
 */
@Component
public class HawkBitMetrics {

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private DeploymentManagement deploymentManagement;

    @Autowired
    private DistributionSetRepository distributionSetRepository;

    @Autowired
    private RolloutManagement rolloutManagement;

    @Autowired
    private SoftwareModuleManagement softwareModuleManagement;

    @Autowired
    private SystemSecurityContext systemSecurityContext;

    @Autowired
    private TargetManagement targetManagement;

    @Autowired
    private TenantMetaDataRepository tenantMetaDataRepository;

    @Autowired
    private EntityManager entityManager;

    private MultiGauge targetMetaDataGauges;
    private MultiGauge installedSoftwareModulesMetaDataGauges;

    @EventListener(ApplicationReadyEvent.class)
    public void registerGauges() {
        registry.gauge("hawkbit_distribution_sets_total", Tags.empty(), this, HawkBitMetrics::distributionSets);
        registry.gauge("hawkbit_rollouts_total", Tags.empty(), this, HawkBitMetrics::rollouts);
        registry.gauge("hawkbit_software_modules_total", Tags.empty(), this, HawkBitMetrics::softwareModules);
        registry.gauge("hawkbit_targets_total", Tags.empty(), this, HawkBitMetrics::targetsTotal);
        registry.gauge("hawkbit_targets_in_time", Tags.empty(), this, HawkBitMetrics::targetsInTime);
        registry.gauge("hawkbit_targets_overdue", Tags.empty(), this, HawkBitMetrics::targetsOverdue);
        registry.gauge("hawkbit_targets_error", Tags.empty(), this, HawkBitMetrics::targetsError);
        registry.gauge("hawkbit_targets_in_sync", Tags.empty(), this, HawkBitMetrics::targetsInSync);
        registry.gauge("hawkbit_targets_pending", Tags.empty(), this, HawkBitMetrics::targetsPending);
        registry.gauge("hawkbit_targets_registered", Tags.empty(), this, HawkBitMetrics::targetsRegistered);
        registry.gauge("hawkbit_targets_unknown", Tags.empty(), this, HawkBitMetrics::targetsUnknown);

        targetMetaDataGauges = MultiGauge.builder("hawkbit_targets").register(registry);
        installedSoftwareModulesMetaDataGauges = MultiGauge.builder("hawkbit_target_installed_sm").register(registry);
    }

    private Long distributionSets() {
        List<String> tenants = getAllTenants();
        Long count = 0L;
        for (String tenant : tenants) {
            count += systemSecurityContext.runAsSystemAsTenant(() -> distributionSetRepository.count(), tenant);
        }
        return count;
    }

    private Long rollouts() {
        List<String> tenants = getAllTenants();
        Long count = 0L;
        for (String tenant : tenants) {
            count += systemSecurityContext.runAsSystemAsTenant(() -> rolloutManagement.count(), tenant);
        }
        return count;
    }

    private Long softwareModules() {
        List<String> tenants = getAllTenants();
        Long count = 0L;
        for (String tenant : tenants) {
            count += systemSecurityContext.runAsSystemAsTenant(() -> softwareModuleManagement.count(), tenant);
        }

        // Update the meta data about installed software modules
        // It is not the most beautiful to do it here but it has to be placed in an callback
        // and this place fits the most.
        updateInstalledSoftwareModulesMetaData();

        return count;
    }

    private Long targetsError() {
        return countTargets(Arrays.asList(TargetUpdateStatus.ERROR), null);
    }

    private Long targetsInSync() {
        return countTargets(Arrays.asList(TargetUpdateStatus.IN_SYNC), null);
    }

    private Long targetsInTime() {
        return countTargets(null, false);
    }

    private Long targetsOverdue() {
        return countTargets(null, true);
    }

    private Long targetsPending() {
        return countTargets(Arrays.asList(TargetUpdateStatus.PENDING), null);
    }

    private Long targetsRegistered() {
        return countTargets(Arrays.asList(TargetUpdateStatus.REGISTERED), null);
    }

    private Long targetsTotal() {
        // Update the meta data about targets.
        // It is not the most beautiful to do it here but it has to be placed in an callback
        // and this place fits the most.
        updateTargetMetaData();

        return countTargets(null, null);
    }

    private Long targetsUnknown() {
        return countTargets(Arrays.asList(TargetUpdateStatus.UNKNOWN), null);
    }

    private void updateTargetMetaData() {
        List<Object[]> targets = entityManager.createNativeQuery("SELECT controller_id, name FROM sp_target")
                .getResultList();
        targetMetaDataGauges.register(targets.stream().map(t -> {
            Tag targetTag = Tag.of("target", t[0].toString());
            Tag nameTag = Tag.of("name", t[1].toString());
            return MultiGauge.Row.of(Tags.of(targetTag, nameTag), 1, Number::doubleValue);
        }).collect(Collectors.toList()));
    }

    private void updateInstalledSoftwareModulesMetaData() {
        List<MultiGauge.Row<?>> softwareModules = new ArrayList<>();
        List<Object[]> installedDistributions = entityManager.createNativeQuery(
                "SELECT t.controller_id, dist.name, dist.version FROM sp_distribution_set dist RIGHT JOIN sp_target t"
                        + " ON dist.id = t.installed_distribution_set WHERE t.installed_distribution_set IS NOT NULL")
                .getResultList();
        for (Object[] d : installedDistributions) {
            Tag targetTag = Tag.of("target", d[0].toString());
            Tag moduleTag = Tag.of("module", d[1].toString());
            Tag versionTag = Tag.of("version", d[2].toString());
            softwareModules.add(MultiGauge.Row.of(Tags.of(targetTag, moduleTag, versionTag), 1, Number::doubleValue));
        }

        List<Object[]> insmodules = entityManager.createNativeQuery(
                "SELECT t.controller_id, sm.name, sm.version FROM sp_base_software_module sm LEFT JOIN sp_ds_module "
                        + "ds_to_sm ON sm.id = ds_to_sm.module_id RIGHT JOIN sp_target t ON ds_to_sm.ds_id = t"
                        + ".installed_distribution_set WHERE t.installed_distribution_set IS NOT NULL")
                .getResultList();
        for (Object[] m : insmodules) {
            Tag targetTag = Tag.of("target", m[0].toString());
            Tag moduleTag = Tag.of("module", m[1].toString());
            Tag versionTag = Tag.of("version", m[2].toString());
            softwareModules.add(MultiGauge.Row.of(Tags.of(targetTag, moduleTag, versionTag), 1, Number::doubleValue));
        }

        installedSoftwareModulesMetaDataGauges.register(softwareModules);
    }

    private Long countTargets(List<TargetUpdateStatus> status, Boolean overdueStatus) {
        List<String> tenants = getAllTenants();
        Long count = 0L;
        for (String tenant : tenants) {
            count += systemSecurityContext.runAsSystemAsTenant(() ->
                    targetManagement.countByFilters(new FilterParams(status, overdueStatus, null, null, null)), tenant);
        }
        return count;
    }

    private List<String> getAllTenants() {
        List<String> tenants = new ArrayList<String>();
        List<JpaTenantMetaData> tenantMetaDataList = tenantMetaDataRepository.findAll();

        for (JpaTenantMetaData metaData : tenantMetaDataList) {
            tenants.add(metaData.getTenant());
        }

        return tenants;
    }
}
