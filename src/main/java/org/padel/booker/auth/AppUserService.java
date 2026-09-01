package org.padel.booker.auth;

import org.padel.booker.booking.BookingScheduler;
import org.padel.booker.config.AppAuthProperties;
import org.padel.booker.domain.AppUser;
import org.padel.booker.domain.BookingStatus;
import org.padel.booker.domain.Credential;
import org.padel.booker.domain.QueuedBooking;
import org.padel.booker.domain.Role;
import org.padel.booker.repo.AppUserRepository;
import org.padel.booker.repo.CredentialRepository;
import org.padel.booker.repo.QueuedBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AppUserService {

    private static final Logger log = LoggerFactory.getLogger(AppUserService.class);
    public static final String RESET_PASSWORD = "changeme";

    private final AppUserRepository users;
    private final CredentialRepository credentials;
    private final QueuedBookingRepository bookings;
    private final BookingScheduler scheduler;
    private final SportyAccountService sportyAccounts;
    private final PasswordEncoder encoder;
    private final AppAuthProperties props;

    public AppUserService(AppUserRepository users, CredentialRepository credentials,
                          QueuedBookingRepository bookings, BookingScheduler scheduler,
                          SportyAccountService sportyAccounts, PasswordEncoder encoder,
                          AppAuthProperties props) {
        this.users = users;
        this.credentials = credentials;
        this.bookings = bookings;
        this.scheduler = scheduler;
        this.sportyAccounts = sportyAccounts;
        this.encoder = encoder;
        this.props = props;
    }

    /** Seed the admin from env on an empty DB, and adopt any pre-existing single-account data. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedAndMigrate() {
        if (users.count() > 0) return;

        String username = props.getUsername();
        String password = props.getPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("No users exist and APP_USERNAME/APP_PASSWORD are not set — cannot seed an admin. Set them and restart.");
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setMustChangePassword(false); // configured via env, treated as already set
        admin = users.save(admin);
        log.info("Seeded admin user '{}'", username);

        // Adopt legacy rows that predate multi-user (the single Sporty account + any bookings).
        for (Credential c : credentials.findByUserIdIsNull()) {
            c.setUserId(admin.getId());
            credentials.save(c);
        }
        for (QueuedBooking b : bookings.findByUserIdIsNull()) {
            b.setUserId(admin.getId());
            bookings.save(b);
        }
    }

    public Optional<AppUser> authenticate(String username, String rawPassword) {
        return users.findByUsername(username)
                .filter(u -> rawPassword != null && encoder.matches(rawPassword, u.getPasswordHash()));
    }

    public AppUser require(long id) {
        return users.findById(id).orElseThrow(() -> new IllegalArgumentException("No user " + id));
    }

    public List<AppUser> list() {
        return users.findAllByOrderByUsernameAsc();
    }

    public boolean hasSportyLinked(long userId) {
        return credentials.findByUserId(userId).map(c -> c.getEmail() != null && !c.getEmail().isBlank()).orElse(false);
    }

    public AppUser create(String username, Role role) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required");
        String clean = username.trim();
        if (users.findByUsername(clean).isPresent()) throw new IllegalArgumentException("Username already exists");
        AppUser u = new AppUser();
        u.setUsername(clean);
        u.setRole(role != null ? role : Role.USER);
        u.setPasswordHash(encoder.encode(RESET_PASSWORD));
        u.setMustChangePassword(true);
        return users.save(u);
    }

    @Transactional
    public void delete(long actingUserId, long targetId) {
        if (actingUserId == targetId) throw new IllegalArgumentException("You can't delete your own account");
        AppUser target = require(targetId);
        if (target.getRole() == Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw new IllegalArgumentException("Can't delete the last admin");
        }
        // Cancel any scheduled fires so they don't run after deletion.
        for (QueuedBooking b : bookings.findByUserIdOrderByTargetFireAtDesc(targetId)) {
            if (b.getStatus() == BookingStatus.PENDING) scheduler.cancel(b.getId());
        }
        sportyAccounts.evict(targetId);
        users.delete(target); // FK cascade removes credentials + queued_bookings (+ attempts)
    }

    public void resetPassword(long targetId) {
        AppUser u = require(targetId);
        u.setPasswordHash(encoder.encode(RESET_PASSWORD));
        u.setMustChangePassword(true);
        users.save(u);
    }

    /** Self change. On a forced change (mustChangePassword) the current password isn't required. */
    public void changePassword(long userId, String currentPassword, String newPassword) {
        AppUser u = require(userId);
        if (!u.isMustChangePassword()) {
            if (currentPassword == null || !encoder.matches(currentPassword, u.getPasswordHash())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
        }
        validatePolicy(newPassword);
        u.setPasswordHash(encoder.encode(newPassword));
        u.setMustChangePassword(false);
        users.save(u);
    }

    private void validatePolicy(String pw) {
        if (pw == null || pw.length() < 6 || pw.chars().noneMatch(Character::isUpperCase)) {
            throw new IllegalArgumentException("Password must be at least 6 characters and contain an uppercase letter");
        }
    }
}
