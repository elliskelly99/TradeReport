package com.TradeReport.TradeReport.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * One place that turns exceptions into HTTP responses, using RFC 7807 ProblemDetail bodies.
 *
 * Extends ResponseEntityExceptionHandler so Spring's own web errors (wrong HTTP method,
 * malformed JSON, missing parameter, ...) get the right 4xx status. We add the domain cases
 * and a final catch-all that hides internals behind a generic 500.
 *
 * 400 bad input | 404 not found | 409 conflicts | 500 anything unexpected
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ReportNotFoundException.class)
    ProblemDetail handleNotFound(ReportNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Report not found", ex.getMessage());
    }

    /** Create refused: this firm and trade reference already have a report. The client updates it instead. */
    @ExceptionHandler(ReportAlreadyExistsException.class)
    ProblemDetail handleAlreadyExists(ReportAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, "Report already exists",
                "A report with this firm and trade reference already exists. Update the existing report instead");
    }

    @ExceptionHandler(DuplicateTradeRefException.class)
    ProblemDetail handleDuplicateInRequest(DuplicateTradeRefException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Duplicate trade reference", ex.getMessage());
    }

    /** A unique constraint (for example (firm, trade_ref_id)) rejected the write. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The request conflicts with existing data, for example a trade reference that already exists for this firm");
    }

    /** Someone else changed the report after we read it (@Version mismatch). Client should re-read and retry. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent update",
                "The report was changed by someone else. Re-read it and try again");
    }

    @ExceptionHandler(UpdateFailDuplicateAmountExistAlreadyException.class)
    ProblemDetail handleDuplicateAmountExistAlready(UpdateFailDuplicateAmountExistAlreadyException ex) {
        return problem(HttpStatus.CONFLICT, "Amount the same as current",
                "The report already exist with the requested amount");
    }

    /** Single-object body that failed @Valid. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> errors.add(describe(null, fe)));
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /** Container body (List<@Valid X>) or constrained parameter that failed validation. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors pe) {
                pe.getFieldErrors().forEach(fe -> errors.add(describe(pe.getContainerIndex(), fe)));
            } else {
                result.getResolvableErrors().forEach(e -> errors.add(e.getDefaultMessage()));
            }
        }
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /** Last resort: log the details, tell the client nothing internal. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "Something went wrong");
    }

    private static ProblemDetail validationProblem(List<String> errors) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", "The request is invalid");
        pd.setProperty("errors", errors);
        return pd;
    }

    private static String describe(Integer index, FieldError fe) {
        String where = index == null ? fe.getField() : "[" + index + "]." + fe.getField();
        return where + ": " + fe.getDefaultMessage();
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
