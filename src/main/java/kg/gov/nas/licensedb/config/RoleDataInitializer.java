package kg.gov.nas.licensedb.config;

import kg.gov.nas.licensedb.entity.Role;
import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.repository.RoleRepository;
import kg.gov.nas.licensedb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;

/**
 * Инициализация (и мягкая миграция) ролей.
 *
 * Зачем это нужно:
 * 1) В Spring Boot 2.7+ data.sql по умолчанию не всегда выполняется для "внешних" БД (MySQL),
 *    поэтому роли могут отсутствовать.
 * 2) Раньше в проекте использовалась роль USER. Сейчас мы вводим OPERATOR.
 *    Для обратной совместимости:
 *    - если есть USER и нет OPERATOR — переименуем USER -> OPERATOR, сохранив id (и связи user_role);
 *    - если есть и USER, и OPERATOR — перенесём назначения USER -> OPERATOR, а USER пометим как DEPRECATED_USER.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleDataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    /**
     * Роли, которые поддерживаются системой (показываются в UI и используются в контроллерах).
     */
    public static final List<String> SUPPORTED_ROLES = Arrays.asList(
        "ADMIN",
        "OPERATOR",
        "PRINTER",
        "VIEWER",
        "AUDITOR"
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateLegacyUserRole();
        ensureRolesExist();
    }

    private void migrateLegacyUserRole() {
        Role operator = roleRepository.findByName("OPERATOR");
        Role legacyUser = roleRepository.findByName("USER");

        if (legacyUser == null) {
            return;
        }

        // 1) Если OPERATOR ещё нет — просто переименуем USER -> OPERATOR (сохраняем id и связи).
        if (operator == null) {
            legacyUser.setName("OPERATOR");
            roleRepository.save(legacyUser);
            log.info("Roles: migrated legacy role USER -> OPERATOR (id={})", legacyUser.getId());
            return;
        }

        // 2) Если OPERATOR уже есть — перенесём назначения USER -> OPERATOR.
        boolean migratedAny = false;
        for (User u : userRepository.findAll()) {
            if (u == null || u.getRoles() == null) continue;
            if (!u.getRoles().contains(legacyUser)) continue;

            u.getRoles().remove(legacyUser);
            u.getRoles().add(operator);
            userRepository.save(u);
            migratedAny = true;
        }

        if (migratedAny) {
            log.info("Roles: reassigned users USER -> OPERATOR (legacyRoleId={}, operatorRoleId={})",
                legacyUser.getId(), operator.getId());
        }

        // Чтобы не путать администратора в UI — помечаем устаревшую роль.
        legacyUser.setName("DEPRECATED_USER");
        roleRepository.save(legacyUser);
        log.info("Roles: renamed legacy role USER -> DEPRECATED_USER (id={})", legacyUser.getId());
    }

    private void ensureRolesExist() {
        for (String roleName : SUPPORTED_ROLES) {
            if (roleRepository.findByName(roleName) == null) {
                Role r = new Role();
                r.setName(roleName);
                roleRepository.save(r);
                log.info("Roles: created missing role {}", roleName);
            }
        }
    }
}
