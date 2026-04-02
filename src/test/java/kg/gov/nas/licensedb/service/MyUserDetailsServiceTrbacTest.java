package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.entity.Role;
import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class MyUserDetailsServiceTrbacTest {

    @AfterEach
    void cleanup() {
        // ничего
    }

    @Test
    void withinWorkingWindow_keepsUserRoles() throws Exception {
        UserRepository repo = Mockito.mock(UserRepository.class);

        User u = User.builder()
                .username("alice")
                .password("x")
                .active(true)
                .roles(Set.of(Role.builder().name("ADMIN").build()))
                .build();

        when(repo.findByUsername("alice")).thenReturn(u);

        MyUserDetailsService svc = newService(repo);

        // гарантируем, что "сейчас" попадает в окно: now-1min .. now+1min
        String tz = "UTC";
        LocalTime now = LocalTime.now(java.time.ZoneId.of(tz));
        setFieldIfExists(svc, "timeZone", tz);
        setFieldIfExists(svc, "workStart", now.minusMinutes(1).toString());
        setFieldIfExists(svc, "workEnd", now.plusMinutes(1).toString());
        setFieldIfExists(svc, "offHoursRole", "VIEWER");

        UserDetails ud = svc.loadUserByUsername("alice");
        Set<String> auth = ud.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        // ожидаем стандартный формат Spring Security
        assertTrue(auth.contains("ROLE_ADMIN"), "В рабочее время должна быть роль ROLE_ADMIN");
        assertFalse(auth.contains("ROLE_VIEWER"), "В рабочее время VIEWER навязываться не должен");
    }

    @Test
    void outsideWorkingWindow_forcesViewerOnly() throws Exception {
        UserRepository repo = Mockito.mock(UserRepository.class);

        User u = User.builder()
                .username("bob")
                .password("x")
                .active(true)
                .roles(Set.of(Role.builder().name("ADMIN").build()))
                .build();

        when(repo.findByUsername("bob")).thenReturn(u);

        MyUserDetailsService svc = newService(repo);

        // гарантируем, что "сейчас" НЕ попадает в окно: now+1min .. now+2min
        String tz = "UTC";
        LocalTime now = LocalTime.now(java.time.ZoneId.of(tz));
        setFieldIfExists(svc, "timeZone", tz);
        setFieldIfExists(svc, "workStart", now.plusMinutes(1).toString());
        setFieldIfExists(svc, "workEnd", now.plusMinutes(2).toString());
        setFieldIfExists(svc, "offHoursRole", "VIEWER");

        UserDetails ud = svc.loadUserByUsername("bob");
        Set<String> auth = ud.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertEquals(Set.of("ROLE_VIEWER"), auth, "Вне рабочего окна должна быть только ROLE_VIEWER");
    }

    // --- helpers ---

    private static MyUserDetailsService newService(UserRepository repo) throws Exception {
        // максимально совместимо: пробуем конструктор (UserRepository), иначе отражением в поле
        for (Constructor<?> c : MyUserDetailsService.class.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 1 && p[0].isAssignableFrom(UserRepository.class)) {
                c.setAccessible(true);
                return (MyUserDetailsService) c.newInstance(repo);
            }
        }
        // fallback: no-arg + set field
        Constructor<MyUserDetailsService> c = MyUserDetailsService.class.getDeclaredConstructor();
        c.setAccessible(true);
        MyUserDetailsService svc = c.newInstance();
        ReflectionTestUtils.setField(svc, "userRepository", repo);
        return svc;
    }

    private static void setFieldIfExists(Object target, String field, Object value) {
        try {
            ReflectionTestUtils.setField(target, field, value);
        } catch (Exception ignored) {
            // если поле называется иначе, надо подстроить тест под реальный код
        }
    }
}
