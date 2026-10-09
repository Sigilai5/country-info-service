package com.ncba.countryinfo.exception;

import static com.ncba.countryinfo.logging.LogConstants.FAILED_STATUS;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.ncba.countryinfo.dto.CountryInfoResponse;
import com.ncba.countryinfo.dto.WsResponse;
import com.ncba.countryinfo.logging.LogConstants;
import com.ncba.countryinfo.logging.RequestLoggingFilter;
import com.ncba.countryinfo.logging.StructuredLog;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception into a {@link WsResponse} with the right HTTP status and a
 * user-friendly message, and logs it. Spring's base class detects the standard framework
 * errors (malformed JSON, wrong method, unsupported media type, unknown path, ...).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fe -> String.valueOf(fe.getDefaultMessage()), (a, b) -> a));

        errorLog(request, HttpStatus.BAD_REQUEST, "Request validation failed: " + errors, "warn")
                .with("validationErrors", errors)
                .write();
        return ResponseEntity.badRequest()
                .body(WsResponse.error(HttpStatus.BAD_REQUEST, "Request validation failed", errors));
    }

    /**
     * Same as above, for endpoints that also validate path/query parameters (e.g. {@code @Positive id}):
     * Spring then validates the whole method and reports body and parameter errors together here.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                bodyErrors.getFieldErrors().forEach(fe ->
                        errors.putIfAbsent(fe.getField(), String.valueOf(fe.getDefaultMessage())));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(err ->
                        errors.putIfAbsent(name, String.valueOf(err.getDefaultMessage())));
            }
        }
        errorLog(request, HttpStatus.BAD_REQUEST, "Request validation failed: " + errors, "warn")
                .with("validationErrors", errors)
                .write();
        return ResponseEntity.badRequest()
                .body(WsResponse.error(HttpStatus.BAD_REQUEST, "Request validation failed", errors));
    }

    /** Every other framework-detected error (400/404/405/415/...) ends up here. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        errorLog(request, statusCode, "Request rejected: " + ex.getMessage(),
                statusCode.is5xxServerError() ? "error" : "warn")
                .with("errorType", ex.getClass().getSimpleName())
                .write();
        return new ResponseEntity<>(WsResponse.error(statusCode, friendlyMessage(ex)), headers, statusCode);
    }

    @ExceptionHandler(CountryNotFoundException.class)
    public ResponseEntity<WsResponse<Void>> handleNotFound(CountryNotFoundException ex, NativeWebRequest request) {
        errorLog(request, HttpStatus.NOT_FOUND, ex.getMessage(), "warn").write();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(WsResponse.error(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    /** Duplicate create: 409, with the existing record in data so the client can use it. */
    @ExceptionHandler(CountryAlreadyExistsException.class)
    public ResponseEntity<WsResponse<CountryInfoResponse>> handleAlreadyExists(CountryAlreadyExistsException ex,
            NativeWebRequest request) {
        errorLog(request, HttpStatus.CONFLICT, ex.getMessage(), "warn").write();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(WsResponse.of(HttpStatus.CONFLICT, ex.getMessage(), ex.getExisting()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<WsResponse<Void>> handleConflict(ConflictException ex, NativeWebRequest request) {
        errorLog(request, HttpStatus.CONFLICT, ex.getMessage(), "warn").write();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(WsResponse.error(HttpStatus.CONFLICT, ex.getMessage()));
    }

    /** A concurrent transaction changed the same row between our read and our write (@Version check). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<WsResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex,
            NativeWebRequest request) {
        errorLog(request, HttpStatus.CONFLICT, "Optimistic lock failure: " + ex.getMessage(), "warn").write();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(WsResponse.error(HttpStatus.CONFLICT,
                "The record was modified by another request. Fetch it again and retry."));
    }

    /** A database constraint (e.g. unique ISO code) rejected the write, typically due to a concurrent change. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<WsResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex,
            NativeWebRequest request) {
        errorLog(request, HttpStatus.CONFLICT, "Data integrity violation: "
                + NestedExceptionUtils.getMostSpecificCause(ex).getMessage(), "warn").write();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(WsResponse.error(HttpStatus.CONFLICT,
                "The request conflicts with existing data (for example a duplicate ISO code)."));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<WsResponse<Void>> handleInvalidRequest(InvalidRequestException ex, NativeWebRequest request) {
        errorLog(request, HttpStatus.BAD_REQUEST, ex.getMessage(), "warn").write();
        return ResponseEntity.badRequest().body(WsResponse.error(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /** Query, lock-wait or transaction timeout detected at the repository layer. */
    @ExceptionHandler(DatabaseTimeoutException.class)
    public ResponseEntity<WsResponse<Void>> handleDatabaseTimeout(DatabaseTimeoutException ex,
            NativeWebRequest request) {
        return databaseUnavailable(ex, request, true);
    }

    /**
     * Database failures that happen outside a repository call, e.g. when a transaction starts and no
     * pooled connection is available in time, the connection drops, or the transaction times out at
     * commit. Timeouts and outages are both transient, so both are 503 with Retry-After.
     */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class,
            TransactionTimedOutException.class, QueryTimeoutException.class,
            PessimisticLockingFailureException.class})
    public ResponseEntity<WsResponse<Void>> handleDatabaseUnavailable(Exception ex, NativeWebRequest request) {
        return databaseUnavailable(ex, request, DatabaseFailures.isTimeout(ex));
    }

    private ResponseEntity<WsResponse<Void>> databaseUnavailable(Exception ex, NativeWebRequest request,
            boolean timeout) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
        String message = timeout
                ? "The database is taking too long to respond. Please try again shortly."
                : "The database is temporarily unavailable. Please try again shortly.";
        errorLog(request, HttpStatus.SERVICE_UNAVAILABLE, (timeout ? "Database timeout: " : "Database unavailable: ")
                + cause.getMessage(), "error")
                .setTargetSystem("MySQL")
                .setLogType(timeout ? "DATABASE_TIMEOUT" : "DATABASE_UNAVAILABLE")
                .with("errorType", cause.getClass().getSimpleName())
                .write();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(WsResponse.error(HttpStatus.SERVICE_UNAVAILABLE, message));
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<WsResponse<Void>> handleUpstream(UpstreamServiceException ex, NativeWebRequest request) {
        Throwable cause = ex.getCause() == null ? null : NestedExceptionUtils.getMostSpecificCause(ex.getCause());
        errorLog(request, HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "error")
                .setTargetSystem("CountryInfoService (SOAP)")
                .with("upstreamException", ex.getCause() == null ? null : ex.getCause().getClass().getName())
                .with("errorType", cause == null ? null : cause.getClass().getSimpleName())
                .with("errorMessage", cause == null ? null : cause.getMessage())
                .write();
        // Retry-After tells well-behaved clients when to try again (matches the breaker's open window).
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(WsResponse.error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<WsResponse<Void>> handleUnexpected(Exception ex, NativeWebRequest request) {
        errorLog(request, HttpStatus.INTERNAL_SERVER_ERROR, "Unhandled error: " + ex.getMessage(), "error")
                .setError(ex)
                .write();
        // Never leak internal details to the client; the requestId links back to the full log.
        return ResponseEntity.internalServerError().body(WsResponse.error(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later."));
    }

    private static String friendlyMessage(Exception ex) {
        return switch (ex) {
            case HttpMessageNotReadableException e -> "Malformed or unreadable JSON request body";
            case HttpRequestMethodNotSupportedException e ->
                    "HTTP method " + e.getMethod() + " is not supported for this endpoint";
            case HttpMediaTypeNotSupportedException e -> "Content-Type must be application/json";
            case NoResourceFoundException e -> "The requested resource was not found";
            case TypeMismatchException e -> "Invalid value '" + e.getValue() + "' for parameter '"
                    + (e instanceof MethodArgumentTypeMismatchException m ? m.getName() : e.getPropertyName()) + "'";
            default -> "The request could not be processed";
        };
    }

    private StructuredLog errorLog(WebRequest request, HttpStatusCode status, String message,
            String level) {
        HttpServletRequest servletRequest = ((NativeWebRequest) request)
                .getNativeRequest(HttpServletRequest.class);
        String endpoint = servletRequest == null ? null
                : servletRequest.getMethod() + " " + servletRequest.getRequestURI();
        return StructuredLog.of(getClass())
                .setLogMessage(message)
                .setLogLevel(level)
                .setResponseCode(status.value())
                .setTargetEndpoint(endpoint)
                .setTargetSystem(LogConstants.THIS_SYSTEM)
                .setProcessName("errorHandling")
                .setOperationName("Error response")
                .setLogType("ERROR")
                .setLogStatus(FAILED_STATUS)
                .setProcessId(MDC.get(LogConstants.MDC_REQUEST_ID))
                .setTransactionCost(servletRequest == null ? 0
                        : RequestLoggingFilter.elapsedMillis(servletRequest));
    }
}
