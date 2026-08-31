package org.padel.booker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    /** Dedicated scheduler for firing queued bookings at their target instants. */
    @Bean
    public ThreadPoolTaskScheduler bookingTaskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(4);
        s.setThreadNamePrefix("booking-fire-");
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.initialize();
        return s;
    }
}
