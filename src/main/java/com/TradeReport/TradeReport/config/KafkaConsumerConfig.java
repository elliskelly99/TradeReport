package com.TradeReport.TradeReport.config;

import com.TradeReport.TradeReport.event.TradeReportEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    /** Key is a Long normally, raw bytes if the key itself failed to deserialize. */
    public static class LongOrBytesSerializer implements Serializer<Object> {
        private final LongSerializer longSerializer = new LongSerializer();

        @Override
        public byte[] serialize(String topic, Object key) {
            if (key == null) return null;
            if (key instanceof Long l) return longSerializer.serialize(topic, l);
            return (byte[]) key;
        }
    }

    /**
     * Retry with backoff, then park the record on trade-report-events.DLT so one bad
     * message never blocks its partition. Deserialization failures are never retried.
     */
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public CommonErrorHandler kafkaErrorHandler(DefaultKafkaProducerFactory<Object, Object> producerFactory,
                                                KafkaTemplate<Long, TradeReportEvent> eventTemplate) {
        KafkaTemplate<Object, Object> bytesTemplate = new KafkaTemplate<>(
                producerFactory.copyWithConfigurationOverride(Map.of(
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongOrBytesSerializer.class,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)));

        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate);
        templates.put(TradeReportEvent.class, eventTemplate);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates,
                (rec, ex) -> new TopicPartition(KafkaTopicConfig.TRADE_REPORT_EVENTS_DLT, rec.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(10_000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
