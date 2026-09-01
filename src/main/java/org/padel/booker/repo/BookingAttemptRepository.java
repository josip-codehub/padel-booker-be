package org.padel.booker.repo;

import org.padel.booker.domain.BookingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookingAttemptRepository extends JpaRepository<BookingAttempt, Long> {

    List<BookingAttempt> findByBookingIdOrderByAttemptAtAsc(Long bookingId);

    /** Recent attempts limited to a set of bookings (one user's). */
    List<BookingAttempt> findTop500ByBookingIdInOrderByAttemptAtDesc(Collection<Long> bookingIds);
}
