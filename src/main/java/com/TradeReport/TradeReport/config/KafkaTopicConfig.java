package com.TradeReport.TradeReport.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;


@Configuration
public class KafkaTopicConfig {
    /*

         https://medium.com/@kanishks772/kafka-replication-explained-so-clearly-itll-make-your-tech-lead-cry-1f0614c60f91
     */

    public static final String TRADE_REPORT_EVENTS = "trade-report-events";

    @Bean
    public NewTopic TradeReportEvent(){
        return TopicBuilder.name(TRADE_REPORT_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
