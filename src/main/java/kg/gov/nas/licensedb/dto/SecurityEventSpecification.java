package kg.gov.nas.licensedb.dto;

import kg.gov.nas.licensedb.entity.SecurityEvent;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Спецификация фильтрации событий безопасности.
 */
public class SecurityEventSpecification implements Specification<SecurityEvent> {
    private final SecurityEventPattern criteria;

    public SecurityEventSpecification(SecurityEventPattern criteria) {
        this.criteria = criteria;
    }

    @Override
    public Predicate toPredicate(Root<SecurityEvent> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        final List<Predicate> ps = new ArrayList<>();

        if (criteria == null) {
            return cb.and(ps.toArray(new Predicate[0]));
        }

        if (notBlank(criteria.getActorUsername())) {
            ps.add(cb.like(cb.lower(root.get("actorUsername")), like(criteria.getActorUsername())));
        }
        if (notBlank(criteria.getSubjectUsername())) {
            ps.add(cb.like(cb.lower(root.get("subjectUsername")), like(criteria.getSubjectUsername())));
        }
        if (notBlank(criteria.getAction())) {
            ps.add(cb.equal(root.get("action"), criteria.getAction().trim()));
        }
        if (notBlank(criteria.getIp())) {
            ps.add(cb.like(cb.lower(root.get("ip")), like(criteria.getIp())));
        }

        // eventMs фильтруется в контроллере через дополнительный Specification (между from/to)
        return cb.and(ps.toArray(new Predicate[0]));
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String like(String s) {
        return "%" + s.trim().toLowerCase() + "%";
    }
}
