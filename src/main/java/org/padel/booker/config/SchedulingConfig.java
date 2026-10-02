package org.padel.booker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableScheduling
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

    /**
     * Pool for fanning out the (slow) per-club availability fetches concurrently so multi-club views
     * don't wait for one club after another.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService availabilityExecutor() {
        return Executors.newFixedThreadPool(6, r -> {
            Thread t = new Thread(r, "availability-fetch");
            t.setDaemon(true);
            return t;
        });
    }
}
