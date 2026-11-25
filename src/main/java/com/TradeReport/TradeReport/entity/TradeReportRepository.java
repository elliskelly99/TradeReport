package com.TradeReport.TradeReport.entity;


import com.TradeReport.TradeReport.DTO.FirmTotal;
import com.TradeReport.TradeReport.DTO.StatusCount;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/*
 * The search queries take LIKE patterns that are already lower case and escaped (escape character '!'),
 * and a non-empty list of statuses. Passing "%" and every status means "no filter", which avoids
 * null parameters (Postgres cannot work out the type of a null in lower()).
 */
@Repository
public interface TradeReportRepository extends
        JpaRepository<TradeReport,Long> {

   boolean existsByTradeRefIdAndFirm(String refId, String firm);

   @Query("""
           select r from TradeReport r
           where lower(r.firm) like :firm escape '!'
             and lower(r.tradeRefId) like :tradeRef escape '!'
             and r.tradeReportStatus in :statuses
           """)
   Page<TradeReport> search(@Param("firm") String firm,
                            @Param("tradeRef") String tradeRef,
                            @Param("statuses") Collection<TradeReportStatus> statuses,
                            Pageable pageable);

   /** Reports per status. The status filter is not applied, so every status can show its count. */
   @Query("""
           select new com.TradeReport.TradeReport.DTO.StatusCount(r.tradeReportStatus, count(r))
           from TradeReport r
           where lower(r.firm) like :firm escape '!'
             and lower(r.tradeRefId) like :tradeRef escape '!'
           group by r.tradeReportStatus
           """)
   List<StatusCount> countByStatus(@Param("firm") String firm, @Param("tradeRef") String tradeRef);

   /** Firms with the biggest total amount first. The pageable only limits how many rows come back. */
   @Query("""
           select new com.TradeReport.TradeReport.DTO.FirmTotal(r.firm, sum(r.amount))
           from TradeReport r
           where lower(r.firm) like :firm escape '!'
             and lower(r.tradeRefId) like :tradeRef escape '!'
             and r.tradeReportStatus in :statuses
           group by r.firm
           order by sum(r.amount) desc
           """)
   List<FirmTotal> topFirmTotals(@Param("firm") String firm,
                                 @Param("tradeRef") String tradeRef,
                                 @Param("statuses") Collection<TradeReportStatus> statuses,
                                 Pageable pageable);

}
