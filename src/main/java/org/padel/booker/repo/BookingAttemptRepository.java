package org.padel.booker.repo;

import org.padel.booker.domain.BookingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingAttemptRepository extends JpaRepository<BookingAttempt, Long> {

    List<BookingAttempt> findByBookingIdOrderByAttemptAtAsc(Long bookingId);

    List<BookingAttempt> findTop500ByOrderByAttemptAtDesc();
}
