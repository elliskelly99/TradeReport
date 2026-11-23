package com.TradeReport.TradeReport.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;


@Configuration
public class KafkaTopicConfig {

    public static final String TRADE_REPORT_EVENTS = "trade-report-events";
    public static final String TRADE_REPORT_EVENTS_DLT = TRADE_REPORT_EVENTS + ".DLT";

    /*
        Partition count caps consumer parallelism: at most `partitions` consumer threads
        (across ALL instances) do work at once. Partitions can only ever be increased.
        Key = reportId, so all events of one report land on one partition, in order.
     */
    @Bean
    public NewTopic tradeReportEventTopic(@Value("${app.kafka.partitions}") int partitions,
                                          @Value("${app.kafka.replicas}") int replicas) {
        return TopicBuilder.name(TRADE_REPORT_EVENTS)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    // DLT must have at least as many partitions as the source topic (same partition is reused).
    @Bean
    public NewTopic tradeReportEventDeadLetterTopic(@Value("${app.kafka.partitions}") int partitions,
                                                    @Value("${app.kafka.replicas}") int replicas) {
        return TopicBuilder.name(TRADE_REPORT_EVENTS_DLT)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
