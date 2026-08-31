package org.padel.booker.repo;

import org.padel.booker.domain.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredentialRepository extends JpaRepository<Credential, Long> {

    /** There is only ever one credential row; grab the first if present. */
    Optional<Credential> findFirstByOrderByIdAsc();
}
