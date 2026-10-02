package org.padel.booker.repo;

import org.padel.booker.domain.FixedBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FixedBookingRepository extends JpaRepository<FixedBooking, Long> {

    List<FixedBooking> findByActiveTrue();

    List<FixedBooking> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<FixedBooking> findByActiveTrueAndCourtIdAndWeekday(Long courtId, int weekday);
}
