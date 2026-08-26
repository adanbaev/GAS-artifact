package kg.gov.nas.licensedb.security;

import kg.gov.nas.licensedb.service.TrbacSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.FilterChain;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Focused validation of request-boundary TRBAC behavior.
 *
 * This test does not start Spring or access the database:
 * TrbacSettingsService is mocked and the real TrbacPerRequestFilter is exercised.
 */
class TrbacPerRequestFilterTest {

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void offHours_downgradesOnlyForCurrentRequest_andRestoresOriginalAuthentication() throws Exception {
        TrbacSettingsService settingsService = mock(TrbacSettingsService.class);
        when(settingsService.getSnapshot()).thenReturn(offHoursSnapshot());

        TrbacPerRequestFilter filter = new TrbacPerRequestFilter(settingsService);

        Authentication original = authenticated("asan", "ROLE_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(original);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/freq/update");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Authentication> seenInsideRequest = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            Authentication current = SecurityContextHolder.getContext().getAuthentication();
            seenInsideRequest.set(current);

            assertNotNull(current);
            assertEquals(Set.of("ROLE_VIEWER"), authorityNames(current),
                    "Outside the working window, downstream request processing must see VIEWER only");
        };

        filter.doFilter(request, response, chain);

        assertNotNull(seenInsideRequest.get());
        assertSame(original, SecurityContextHolder.getContext().getAuthentication(),
                "After request completion, the original authentication must be restored");
        assertEquals(Set.of("ROLE_ADMIN"),
                authorityNames(SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    void withinWorkingHours_preservesOriginalAuthority() throws Exception {
        TrbacSettingsService settingsService = mock(TrbacSettingsService.class);
        when(settingsService.getSnapshot()).thenReturn(withinHoursSnapshot());

        TrbacPerRequestFilter filter = new TrbacPerRequestFilter(settingsService);

        Authentication original = authenticated("asan", "ROLE_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(original);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/freq/update");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            Authentication current = SecurityContextHolder.getContext().getAuthentication();

            assertSame(original, current,
                    "Inside the working window, the filter must not replace the authentication");
            assertEquals(Set.of("ROLE_ADMIN"), authorityNames(current));
        };

        filter.doFilter(request, response, chain);

        assertSame(original, SecurityContextHolder.getContext().getAuthentication());
        assertEquals(Set.of("ROLE_ADMIN"),
                authorityNames(SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    void downstreamAuthenticationChange_isNotOverwrittenByFinallyRestore() throws Exception {
        TrbacSettingsService settingsService = mock(TrbacSettingsService.class);
        when(settingsService.getSnapshot()).thenReturn(offHoursSnapshot());

        TrbacPerRequestFilter filter = new TrbacPerRequestFilter(settingsService);

        Authentication original = authenticated("asan", "ROLE_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(original);

        Authentication downstreamAuthentication = authenticated("asan", "ROLE_AUDITOR");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/account/change-password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            Authentication current = SecurityContextHolder.getContext().getAuthentication();

            assertEquals(Set.of("ROLE_VIEWER"), authorityNames(current),
                    "TRBAC downgrade must be active while downstream processing begins");

            // Simulate an intentional authentication replacement by downstream security/application logic.
            SecurityContextHolder.getContext().setAuthentication(downstreamAuthentication);
        };

        filter.doFilter(request, response, chain);

        assertSame(downstreamAuthentication, SecurityContextHolder.getContext().getAuthentication(),
                "TRBAC finally block must not overwrite an authentication deliberately changed downstream");
        assertEquals(Set.of("ROLE_AUDITOR"),
                authorityNames(SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    void disabledTrbac_doesNotModifyAuthentication() throws Exception {
        TrbacSettingsService settingsService = mock(TrbacSettingsService.class);
        when(settingsService.getSnapshot()).thenReturn(
                new TrbacSettingsService.SettingsSnapshot(
                        false, "09:00", "18:00", "UTC", "VIEWER", null, null)
        );

        TrbacPerRequestFilter filter = new TrbacPerRequestFilter(settingsService);

        Authentication original = authenticated("asan", "ROLE_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(original);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/freq/update");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            Authentication current = SecurityContextHolder.getContext().getAuthentication();
            assertSame(original, current);
            assertEquals(Set.of("ROLE_ADMIN"), authorityNames(current));
        };

        filter.doFilter(request, response, chain);

        assertSame(original, SecurityContextHolder.getContext().getAuthentication());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static Authentication authenticated(String username, String... authorities) {
        List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return new UsernamePasswordAuthenticationToken(username, "n/a", granted);
    }

    private static Set<String> authorityNames(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    /**
     * Creates a short allowed window about six hours in the future.
     * Therefore "now" is deterministically outside the configured window
     * without changing the production filter or injecting a test clock.
     */
    private static TrbacSettingsService.SettingsSnapshot offHoursSnapshot() {
        ZoneId zone = ZoneId.of("UTC");
        LocalTime now = LocalTime.now(zone);
        LocalTime start = now.plusHours(6);
        LocalTime end = start.plusMinutes(1);

        return new TrbacSettingsService.SettingsSnapshot(
                true,
                start.toString(),
                end.toString(),
                zone.getId(),
                "VIEWER",
                null,
                null
        );
    }

    /**
     * Creates a window that safely contains the current time.
     * The production filter's overnight-window logic also handles midnight crossing.
     */
    private static TrbacSettingsService.SettingsSnapshot withinHoursSnapshot() {
        ZoneId zone = ZoneId.of("UTC");
        LocalTime now = LocalTime.now(zone);
        LocalTime start = now.minusMinutes(5);
        LocalTime end = now.plusMinutes(5);

        return new TrbacSettingsService.SettingsSnapshot(
                true,
                start.toString(),
                end.toString(),
                zone.getId(),
                "VIEWER",
                null,
                null
        );
    }
}
