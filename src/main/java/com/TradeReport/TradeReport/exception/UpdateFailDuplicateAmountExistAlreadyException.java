package com.TradeReport.TradeReport.exception;

import java.math.BigDecimal;

public class UpdateFailDuplicateAmountExistAlreadyException extends RuntimeException {
    public UpdateFailDuplicateAmountExistAlreadyException(String firm, BigDecimal amount){
        super("Duplicate trade reference in request: firm='" + firm + "', tradeRefId='" + amount + "'");
    }
}
