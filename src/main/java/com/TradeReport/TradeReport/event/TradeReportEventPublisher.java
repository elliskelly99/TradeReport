package com.TradeReport.TradeReport.event;

import com.TradeReport.TradeReport.config.KafkaTopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
public class TradeReportEventPublisher {
    public TradeReportEventPublisher(KafkaTemplate<Long, TradeReportEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    private final KafkaTemplate<Long, TradeReportEvent> kafkaTemplate;

    /*
      ENSURING WE ONLY ALLOW THE LISTENER TO WORK TILL AFTER WE HAVE MADE A
      MESSAGE TILL AFTER THE TRANSACTION IS COMMITTED
    */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendMessage(TradeReportEvent event){
        kafkaTemplate.send(KafkaTopicConfig.TRADE_REPORT_EVENTS,
                        event.reportId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for report {}",
                                event.eventType(), event.reportId(), ex);
                    } else {
                        log.info("Published {} for report {} to partition {} offset {}",
                                event.eventType(), event.reportId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
