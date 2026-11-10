package com.TradeReport.TradeReport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TradeReportApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradeReportApplication.class, args);
	}
	/*
			Goal:
				Store trade reports

				create, updated, cancelled

				regulators:
					- Time stamps
					- Once created audits must be not be immutiable

			Assumptions:
				- H2 DB for local testing (will need to test services and controllers after we have configed to our
					chosen db)
				- We will need an audit trail for the trade
					- Must be idempotent once appended to the trade

			Entities:
				- TradeReports
					- ID
					- Amount
					- firm
					- date

				- TradeReportAduits (Many to one) fk on trade.id
					- ID
					- User
					- date

			Operations:
				- Accepts submited reports
				- accepts corrections
				- accepts cancelectations
				- audit history
				- look up report
				- look up ticketReport history by id
	 */
}
