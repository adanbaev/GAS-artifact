package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.dto.IntegrityCheckReport;
import kg.gov.nas.licensedb.security.WebSecurityConfig;
import kg.gov.nas.licensedb.service.IntegrityService;
import kg.gov.nas.licensedb.service.MyUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = IntegrityController.class)
@Import(WebSecurityConfig.class)
class IntegrityControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IntegrityService integrityService;

    // нужны, потому что WebSecurityConfig их @Autowired
    @MockBean
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @MockBean
    private MyUserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessIntegrityCheck() throws Exception {
        when(integrityService.check(true, 2000)).thenReturn(
                IntegrityCheckReport.builder()
                        .ok(true)
                        .checkedAtMs(System.currentTimeMillis())
                        .logEntriesChecked(1)
                        .chainIssues(0)
                        .dataIssues(0)
                        .issues(List.of())
                        .build()
        );

        mockMvc.perform(get("/admin/integrity/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminIsDenied() throws Exception {
        mockMvc.perform(get("/admin/integrity/check"))
                .andExpect(status().isForbidden());
    }
}
