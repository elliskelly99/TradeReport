package com.TradeReport.TradeReport.entity;

import com.TradeReport.TradeReport.entities.TradeReportsAudits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeReportsAuditsRepository extends
        JpaRepository<TradeReportsAudits, Long> {
        List <TradeReportsAudits> findByTradeReportIdOrderByReportVersionAsc(Long Id);//AUDITS CAN BE CONSUMED OUT OF ORDER USE THE TRADE REPORT VERSION AS THE SOURCE OF TRUTH WHEN FETCHING THE AUDITS

        boolean existsByEventId(java.util.UUID eventId);
    }
