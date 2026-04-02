package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.config.RoleDataInitializer;
import kg.gov.nas.licensedb.entity.Role;
import kg.gov.nas.licensedb.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RoleService {
    private final RoleRepository roleRepository;

    @Autowired
    public RoleService(
        RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<Role> getAll() {
        // В UI показываем только поддерживаемые роли и в фиксированном порядке.
        return roleRepository.findAll().stream()
            .filter(Objects::nonNull)
            .filter(r -> r.getName() != null && RoleDataInitializer.SUPPORTED_ROLES.contains(r.getName()))
            .sorted(Comparator.comparingInt(r -> RoleDataInitializer.SUPPORTED_ROLES.indexOf(r.getName())))
            .collect(Collectors.toList());
    }
}
