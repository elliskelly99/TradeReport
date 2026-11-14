package com.TradeReport.TradeReport.event;

import com.TradeReport.TradeReport.config.KafkaTopicConfig;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.service.TradeReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TradeReportConsumer {

    @KafkaListener(topics = KafkaTopicConfig.TRADE_REPORT_EVENTS, groupId = "trade-report-service")
    public void consume(
            @Payload TradeReportEvent event,
            @Header (KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
            ) {

        log.info("Received event={} from partition={} at offset={}", event, partition, offset);
    }
}
