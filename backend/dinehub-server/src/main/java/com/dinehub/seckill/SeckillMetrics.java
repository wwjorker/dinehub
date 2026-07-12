package com.dinehub.seckill;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class SeckillMetrics {

    private final Counter admitted;
    private final Counter settled;
    private final Counter compensated;
    private final Counter publishRetries;
    private final Counter publishFailures;
    private final Timer admissionLatency;
    private final Timer settlementLatency;

    public SeckillMetrics(MeterRegistry registry) {
        admitted = registry.counter("dinehub.seckill.admitted");
        settled = registry.counter("dinehub.seckill.settled");
        compensated = registry.counter("dinehub.seckill.compensated");
        publishRetries = registry.counter("dinehub.seckill.publish.retries");
        publishFailures = registry.counter("dinehub.seckill.publish.failures");
        admissionLatency = registry.timer("dinehub.seckill.admission.latency");
        settlementLatency = registry.timer("dinehub.seckill.settlement.latency");
    }

    public Long recordAdmission(Supplier<Long> action) {
        return admissionLatency.record(action);
    }

    public void admitted() {
        admitted.increment();
    }

    public void settled(Long admittedAt) {
        settled.increment();
        if (admittedAt != null && admittedAt > 0) {
            long elapsed = Math.max(0L, System.currentTimeMillis() - admittedAt);
            settlementLatency.record(Duration.ofMillis(elapsed));
        }
    }

    public void compensated() {
        compensated.increment();
    }

    public void publishRetry() {
        publishRetries.increment();
    }

    public void publishFailure() {
        publishFailures.increment();
    }
}
