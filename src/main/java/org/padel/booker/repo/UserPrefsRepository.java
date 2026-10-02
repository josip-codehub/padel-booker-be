package org.padel.booker.repo;

import org.padel.booker.domain.UserPrefs;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPrefsRepository extends JpaRepository<UserPrefs, Long> {
    Optional<UserPrefs> findByUserId(Long userId);
}
