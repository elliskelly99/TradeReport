package com.TradeReport.TradeReport.repository;

import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportsAudits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeReportsAuditsRepository extends
        JpaRepository<TradeReportsAudits, Long> {
        List <TradeReportsAudits> findByTradeReportId(Long Id);
    }
