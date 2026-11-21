package com.TradeReport.TradeReport.event;

import com.TradeReport.TradeReport.config.KafkaTopicConfig;
import com.TradeReport.TradeReport.repository.TradeReportsAuditsRepository;
import com.TradeReport.TradeReport.service.TradeReportAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Turns each TradeReportEvent into one audit row.
 *
 * Scale-out: every instance uses the same consumer group (spring.kafka.consumer.group-id),
 * so Kafka splits the topic's partitions across instances and threads.
 *
 * Idempotency: audit.event_id is UNIQUE. A redelivered message hits the constraint and is
 * acknowledged as a duplicate. The offset is only committed after this method returns.
 */

@Service
public class TradeReportConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeReportConsumer.class);

    private final TradeReportAuditService auditService;
    private final TradeReportsAuditsRepository auditsRepository;

    public TradeReportConsumer(TradeReportAuditService auditService,
                               TradeReportsAuditsRepository auditsRepository) {
        this.auditService = auditService;
        this.auditsRepository = auditsRepository;
    }

    @KafkaListener(topics = KafkaTopicConfig.TRADE_REPORT_EVENTS)
    public void consume(
            @Payload TradeReportEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Received event={} from partition={} at offset={}", event.eventId(), partition, offset);
        try {
            auditService.recordAudit(event);
            log.info("Audit COMMITTED for event={} report={} version={} (partition={} offset={})",
                    event.eventId(), event.reportId(), event.version(), partition, offset);
        } catch (DataIntegrityViolationException e) {
            // Duplicate delivery -> already audited. Anything else (e.g. missing report FK) is a real failure.
            if (auditsRepository.existsByEventId(event.eventId())) {
                log.info("Duplicate event {} ignored (already audited)", event.eventId());
                return;
            }
            throw e;
        }
    }
}
