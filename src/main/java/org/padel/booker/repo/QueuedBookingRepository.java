package org.padel.booker.repo;

import org.padel.booker.domain.BookingStatus;
import org.padel.booker.domain.QueuedBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueuedBookingRepository extends JpaRepository<QueuedBooking, Long> {

    List<QueuedBooking> findByStatusOrderByTargetFireAtAsc(BookingStatus status);

    List<QueuedBooking> findAllByOrderByTargetFireAtDesc();
}
