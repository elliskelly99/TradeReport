package com.TradeReport.TradeReport.entity;


import com.TradeReport.TradeReport.entities.TradeReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeReportRepository extends
        JpaRepository<TradeReport,Long> {
}
