package org.padel.booker.repo;

import org.padel.booker.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    List<AppUser> findAllByOrderByUsernameAsc();
    long countByRole(org.padel.booker.domain.Role role);
}
