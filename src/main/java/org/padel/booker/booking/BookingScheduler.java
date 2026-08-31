package org.padel.booker.booking;

import org.padel.booker.config.SportyProperties;
import org.padel.booker.domain.BookingStatus;
import org.padel.booker.domain.QueuedBooking;
import org.padel.booker.repo.QueuedBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Schedules each PENDING booking to fire at (a bit before) its target instant. Reloads all pending
 * bookings on startup, so nothing is lost across restarts/reboots.
 */
@Component
public class BookingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingScheduler.class);

    private final ThreadPoolTaskScheduler taskScheduler;
    private final QueuedBookingRepository bookings;
    private final BookingExecutor executor;
    private final SportyProperties props;

    private final ConcurrentHashMap<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public BookingScheduler(ThreadPoolTaskScheduler bookingTaskScheduler,
                            QueuedBookingRepository bookings,
                            BookingExecutor executor,
                            SportyProperties props) {
        this.taskScheduler = bookingTaskScheduler;
        this.bookings = bookings;
        this.executor = executor;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reloadPending() {
        List<QueuedBooking> pending = bookings.findByStatusOrderByTargetFireAtAsc(BookingStatus.PENDING);
        log.info("Reloading {} pending booking(s) on startup", pending.size());
        for (QueuedBooking b : pending) {
            schedule(b);
        }
    }

    public void schedule(QueuedBooking booking) {
        long wakeBeforeSeconds = props.getBooking().getClockSyncLeadSeconds() + props.getBooking().getPreRollSeconds();
        Instant wakeAt = booking.getTargetFireAt().minusSeconds(wakeBeforeSeconds);
        Instant earliest = Instant.now().plusSeconds(1);
        if (wakeAt.isBefore(earliest)) {
            wakeAt = earliest; // target already open (within window) -> fire right away
        }
        Long id = booking.getId();
        ScheduledFuture<?> future = taskScheduler.schedule(() -> safeRun(id), wakeAt);
        ScheduledFuture<?> old = futures.put(id, future);
        if (old != null) old.cancel(false);
        log.info("Scheduled booking {} to wake at {} (target {})", id, wakeAt, booking.getTargetFireAt());
    }

    public void cancel(Long bookingId) {
        ScheduledFuture<?> future = futures.remove(bookingId);
        if (future != null) {
            future.cancel(false);
            log.info("Cancelled scheduled fire for booking {}", bookingId);
        }
    }

    private void safeRun(long bookingId) {
        try {
            executor.run(bookingId);
        } catch (RuntimeException e) {
            log.error("Unexpected error firing booking {}", bookingId, e);
        } finally {
            futures.remove(bookingId);
        }
    }
}
