package kg.gov.nas.licensedb.dto;

import kg.gov.nas.licensedb.entity.Role;
import kg.gov.nas.licensedb.entity.User;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Спецификация фильтрации пользователей в админке.
 * Важно: User.roles — связь ManyToMany, поэтому фильтрация по роли делается через join("roles").
 */
public class UserSpecification implements Specification<User> {
    private final UserPattern criteria;

    public UserSpecification(UserPattern criteria) {
        this.criteria = criteria;
    }

    @Override
    public Predicate toPredicate(Root<User> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder criteriaBuilder) {
        final List<Predicate> predicates = new ArrayList<>();

        predicates.add(criteriaBuilder.isTrue(root.get("active")));

        if (criteria.getUsername() != null && !criteria.getUsername().isEmpty()) {
            predicates.add(criteriaBuilder.like(
                criteriaBuilder.lower(root.get("username")),
                getContainsLikePattern(criteria.getUsername())
            ));
        }

        if (criteria.getRoleId() != null) {
            Join<User, Role> rolesJoin = root.join("roles");
            predicates.add(criteriaBuilder.equal(rolesJoin.get("id"), criteria.getRoleId()));
            criteriaQuery.distinct(true);
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    public static String getContainsLikePattern(String searchTerm) {
        return (searchTerm == null || searchTerm.isEmpty()) ? "%" : "%" + searchTerm.toLowerCase() + "%";
    }
}
