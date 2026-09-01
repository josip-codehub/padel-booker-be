package org.padel.booker.repo;

import org.padel.booker.domain.BookingStatus;
import org.padel.booker.domain.QueuedBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QueuedBookingRepository extends JpaRepository<QueuedBooking, Long> {

    /** All bookings in a status (scheduler reload; availability overlay; double-schedule checks). */
    List<QueuedBooking> findByStatusOrderByTargetFireAtAsc(BookingStatus status);

    /** One user's bookings, newest first (Queue page). */
    List<QueuedBooking> findByUserIdOrderByTargetFireAtDesc(Long userId);

    /** Legacy rows not yet tied to a user. */
    List<QueuedBooking> findByUserIdIsNull();

    /** Is this exact slot already scheduled by anyone (to prevent double-scheduling)? */
    Optional<QueuedBooking> findFirstByStatusAndCourtIdAndPlayDateAndSlotTime(
            BookingStatus status, Long courtId, LocalDate playDate, int slotTime);
}
