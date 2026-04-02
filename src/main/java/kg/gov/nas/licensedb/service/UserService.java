package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.entity.Role;
import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.repository.RoleRepository;
import kg.gov.nas.licensedb.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@Service
public class UserService {
    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    public Page<User> get(Specification specification, Pageable pageable){
        return userRepository.findAll(specification, pageable);
    }

    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User findUserByUserName(String userName) {
        return userRepository.findByUsername(userName);
    }

    public User getById(Long id){
        return userRepository.findById(id).orElseThrow();
    }

    public User saveUser2(User user) {
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        user.setActive(true);

        // ВАЖНО: по умолчанию не выдаём ADMIN. Новому пользователю даём OPERATOR.
        // Если роль ещё не создана (например, пустая БД) — пробуем legacy USER, затем VIEWER.
        Role userRole = roleRepository.findByName("OPERATOR");
        if (userRole == null) {
            userRole = roleRepository.findByName("USER"); // обратная совместимость
        }
        if (userRole == null) {
            userRole = roleRepository.findByName("VIEWER");
        }

        user.setRoles(new HashSet<Role>(Arrays.asList(userRole)));
        return userRepository.save(user);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public void delete(User user){
        user.setActive(false);
        userRepository.save(user);
    }
}
