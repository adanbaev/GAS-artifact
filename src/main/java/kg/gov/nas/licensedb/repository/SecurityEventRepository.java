package kg.gov.nas.licensedb.repository;

import kg.gov.nas.licensedb.entity.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long>, JpaSpecificationExecutor<SecurityEvent> {

    @Query("select distinct e.action from SecurityEvent e order by e.action")
    List<String> findDistinctActions();
}
