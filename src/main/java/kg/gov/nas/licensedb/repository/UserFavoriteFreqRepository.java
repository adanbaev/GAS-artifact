package kg.gov.nas.licensedb.repository;

import kg.gov.nas.licensedb.entity.UserFavoriteFreqId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFavoriteFreqRepository extends JpaRepository<UserFavoriteFreqId, Long> {

    List<UserFavoriteFreqId> findTop20ByUser_IdOrderByCreatedAtMsDesc(Long userId);

    List<UserFavoriteFreqId> findByUser_IdOrderByCreatedAtMsDesc(Long userId);

    Optional<UserFavoriteFreqId> findByIdAndUser_Id(Long id, Long userId);
}
