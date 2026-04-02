package kg.gov.nas.licensedb.config;

import kg.gov.nas.licensedb.entity.Role;
import kg.gov.nas.licensedb.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Конвертер для биндинга ролей из HTML-форм.
 *
 * В admin/user/user.html select отправляет roleId (String). Spring MVC должен
 * преобразовать его в Role для поля User.roles (Set<Role>). Без этого конвертера
 * выбор ролей в форме часто не работает корректно.
 */
@Component
@RequiredArgsConstructor
public class RoleIdToRoleConverter implements Converter<String, Role> {

    private final RoleRepository roleRepository;

    @Override
    public Role convert(String source) {
        if (source == null) return null;
        String s = source.trim();
        if (s.isEmpty()) return null;

        // обычно приходит id
        try {
            Long id = Long.parseLong(s);
            return roleRepository.findById(id).orElse(null);
        } catch (NumberFormatException ignore) {
            // на всякий случай поддержим вариант, если прилетит имя роли
            return roleRepository.findByName(s);
        }
    }
}