package org.eclipse.hawkbit.security;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.hawkbit.dmf.hono.HonoDeviceSync;
import org.eclipse.hawkbit.dmf.hono.model.DeviceSecret;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.Collection;
import java.util.List;

@Slf4j
public class PreAuthHonoAuthenticationProvider extends PreAuthTokenSourceTrustAuthenticationProvider {

    private final HonoDeviceSync honoDeviceSync;

    public PreAuthHonoAuthenticationProvider(HonoDeviceSync honoDeviceSync) {
        super();
        this.honoDeviceSync = honoDeviceSync;
    }

    public PreAuthHonoAuthenticationProvider(HonoDeviceSync honoDeviceSync, final List<String> authorizedSourceIps) {
        super(authorizedSourceIps);
        this.honoDeviceSync = honoDeviceSync;
    }

    public PreAuthHonoAuthenticationProvider(HonoDeviceSync honoDeviceSync, final String... authorizedSourceIps) {
        super(authorizedSourceIps);
        this.honoDeviceSync = honoDeviceSync;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!supports(authentication.getClass())) {
            return null;
        }
        log.info("{}", authentication);
        final PreAuthenticatedAuthenticationToken token = (PreAuthenticatedAuthenticationToken) authentication;
        final Object credentials = token.getCredentials();
        final Object principal = token.getPrincipal();
        final Object tokenDetails = token.getDetails();
        final Collection<GrantedAuthority> authorities = token.getAuthorities();

        if (!(principal instanceof HeaderAuthentication) || !(credentials instanceof DeviceSecret)) {
            throw new BadCredentialsException("The provided principal and credentials are not match");
        }
        DeviceSecret secret = (DeviceSecret) credentials;

        boolean successAuthentication = new HeaderAuthentication(secret.getDeviceId(),
                new String(secret.getSecret().getValue()))
                .equals(principal);

        if (successAuthentication) {
            if (tokenDetails instanceof TenantAwareWebAuthenticationDetails) {
                TenantAwareWebAuthenticationDetails tenantAwareTokenDetails =
                        (TenantAwareWebAuthenticationDetails) tokenDetails;
                honoDeviceSync.checkDeviceIfAbsentSync(tenantAwareTokenDetails.getTenant(),
                        secret.getDeviceId());
            }

            final PreAuthenticatedAuthenticationToken successToken = new PreAuthenticatedAuthenticationToken(principal,
                    credentials, authorities);
            successToken.setDetails(tokenDetails);
            return successToken;
        }

        throw new BadCredentialsException("The provided principal and credentials are not match");
    }
}
