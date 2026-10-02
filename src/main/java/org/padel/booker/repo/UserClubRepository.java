package org.padel.booker.repo;

import org.padel.booker.domain.UserClub;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserClubRepository extends JpaRepository<UserClub, Long> {

    List<UserClub> findByUserId(Long userId);

    List<UserClub> findByUserIdAndEnabledTrueOrderByClubId(Long userId);

    Optional<UserClub> findByUserIdAndClubId(Long userId, Long clubId);
}
