package org.padel.booker.repo;

import org.padel.booker.domain.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CredentialRepository extends JpaRepository<Credential, Long> {

    /** The Sporty Plus account for a given app user. */
    Optional<Credential> findByUserId(Long userId);

    /** Legacy single-account rows not yet tied to a user (adopted by the seeded admin on startup). */
    List<Credential> findByUserIdIsNull();
}
