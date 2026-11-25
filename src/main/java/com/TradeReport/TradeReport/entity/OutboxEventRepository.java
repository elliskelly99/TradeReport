package com.TradeReport.TradeReport.entity;

import com.TradeReport.TradeReport.entities.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * SKIP LOCKED lets several app instances relay concurrently without (NOT IN SCOPE BUT INSURANCE FOR FUTURE HORITZONTAL SCALING
     * claiming the same rows.
     */
    @Query(value = "SELECT * FROM outbox_event WHERE published_at IS NULL ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> claimBatch(@Param("limit") int limit);
}
