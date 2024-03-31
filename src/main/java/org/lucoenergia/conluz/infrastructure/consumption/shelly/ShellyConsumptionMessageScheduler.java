package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import jakarta.annotation.PreDestroy;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.persist.PersistShellyConsumptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ShellyConsumptionMessageScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShellyConsumptionMessageScheduler.class);

    private final PersistShellyConsumptionRepository persistShellyConsumptionRepository;
    private final LinkedBlockingQueue<ShellyInstantConsumption> queue;
    private final ScheduledExecutorService executorService;

    public ShellyConsumptionMessageScheduler(PersistShellyConsumptionRepository persistShellyConsumptionRepository) {
        this.persistShellyConsumptionRepository = persistShellyConsumptionRepository;
        this.queue = new LinkedBlockingQueue<>();
        this.executorService = Executors.newScheduledThreadPool(1);
        /*
         * This setup will retrieve up to 100 messages every second from the queue and write them to the InfluxDB. If there are less than 100 messages,
         * it will take what's there. If there are more, it will take the first 100 and leave the rest for the next call.
         */
        long initDelay = 0; // The delay before the task is to be executed for the first time.
        long period = 5; // The time interval to wait between the end of the previous execution and the start of the next.
        executorService.scheduleAtFixedRate(() -> {
            List<ShellyInstantConsumption> batch = new ArrayList<>();
            queue.drainTo(batch, 100);

            if (!batch.isEmpty()) {
                this.persistShellyConsumptionRepository.persistInstantConsumptions(batch);
            }
        }, initDelay, period, TimeUnit.SECONDS);
    }

    public void putInQueue(ShellyInstantConsumption consumption) {
        try {
            queue.put(consumption);
        } catch (InterruptedException e) {
            LOGGER.error("Unable to enqueue message {}. Reason: {}", consumption, e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Shutdown executorService when application is stopping
     * With @PreDestroy, whenever the application shutdowns, it will call stopService() for you ensuring proper shutdown
     * of your executorService.
     * The awaitTermination method will block until all tasks have completed execution after a shutdown request,
     * or the timeout occurs, or the current thread is interrupted, whichever happens first.
     */
    @PreDestroy
    public void stopService() {
        executorService.shutdown();
        try {
            // waiting for all tasks to finish before we proceed.
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
                    LOGGER.error("Unable to terminate executorService");
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
