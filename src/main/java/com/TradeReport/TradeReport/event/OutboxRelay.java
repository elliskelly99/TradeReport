package com.TradeReport.TradeReport.event;

import com.TradeReport.TradeReport.config.KafkaTopicConfig;
import com.TradeReport.TradeReport.entities.OutboxEvent;
import com.TradeReport.TradeReport.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes committed outbox rows to Kafka. Safe to run on every instance:
 * FOR UPDATE SKIP LOCKED makes instances claim disjoint batches.
 * Delivery is at-least-once (a crash after send but before commit re-sends),
 * which the idempotent consumer absorbs.
 */
@Service
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Long, TradeReportEvent> kafkaTemplate;
    private final int batchSize;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                       KafkaTemplate<Long, TradeReportEvent> kafkaTemplate,
                       @Value("${app.outbox.relay.batch-size:100}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.poll-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outboxEventRepository.claimBatch(batchSize);
        for (OutboxEvent row : batch) {
            TradeReportEvent event = row.toEvent();
            try {
                kafkaTemplate.send(KafkaTopicConfig.TRADE_REPORT_EVENTS, event.reportId(), event)
                        .get(10, TimeUnit.SECONDS);//Wait at most 10 seconds to acknowledge that the broker received the message
                row.markPublished();
            } catch (Exception e) {
                // Stop at the first failure so later events are not published ahead of this one.
                log.error("Outbox publish failed for event {} (report {}); will retry", event.eventId(), event.reportId(), e);
                return;
            }
        }
        if (!batch.isEmpty()) {
            log.debug("Relayed {} outbox events", batch.size());
        }
    }
}
