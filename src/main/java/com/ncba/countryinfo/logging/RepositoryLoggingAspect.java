package com.ncba.countryinfo.logging;

import static com.ncba.countryinfo.logging.LogConstants.FAILED_STATUS;
import static com.ncba.countryinfo.logging.LogConstants.SUCCESS_STATUS;

import java.util.Collection;
import java.util.Optional;

import com.ncba.countryinfo.exception.DatabaseFailures;
import com.ncba.countryinfo.exception.DatabaseTimeoutException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Logs every Spring Data repository call (i.e. every trip to MySQL) with its duration and
 * outcome. Only a summary of the result is logged (row count / found or not), never entity
 * contents. Timeouts (query, lock wait, transaction, connection pool) are logged as
 * DATABASE_TIMEOUT and rethrown as {@link DatabaseTimeoutException} (503 to the client).
 */
@Aspect
@Component
public class RepositoryLoggingAspect {

    @Around("this(org.springframework.data.repository.Repository)")
    public Object logRepositoryCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String repository = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String operation = repository + "." + joinPoint.getSignature().getName();
        try {
            Object result = joinPoint.proceed();
            StructuredLog.of(getClass())
                    .setLogMessage("Database call succeeded: " + operation)
                    .setLogLevel("info")
                    .setTargetEndpoint(operation)
                    .setTargetSystem("MySQL")
                    .setProcessName(joinPoint.getSignature().getName())
                    .setOperationName("Database " + joinPoint.getSignature().getName())
                    .setLogType("DATABASE")
                    .setLogStatus(SUCCESS_STATUS)
                    .setProcessId(MDC.get(LogConstants.MDC_REQUEST_ID))
                    .with("result", summarize(result))
                    .setTransactionCost(System.currentTimeMillis() - startTime)
                    .write();
            return result;
        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            boolean timeout = DatabaseFailures.isTimeout(ex);
            // Constraint violations (e.g. two requests inserting the same country) are handled by the
            // caller, so they are warnings rather than errors.
            StructuredLog.of(getClass())
                    .setLogMessage((timeout ? "Database call timed out: " : "Database call failed: ") + operation
                            + (timeout ? " after " + elapsed + "ms" : ""))
                    .setLogLevel(ex instanceof DataIntegrityViolationException ? "warn" : "error")
                    .setResponseCode(timeout ? "503" : null)
                    .setTargetEndpoint(operation)
                    .setTargetSystem("MySQL")
                    .setProcessName(joinPoint.getSignature().getName())
                    .setOperationName("Database " + joinPoint.getSignature().getName())
                    .setLogType(timeout ? "DATABASE_TIMEOUT" : "DATABASE")
                    .setLogStatus(FAILED_STATUS)
                    .setProcessId(MDC.get(LogConstants.MDC_REQUEST_ID))
                    .setError(ex)
                    .setTransactionCost(elapsed)
                    .write();
            if (timeout && !(ex instanceof DatabaseTimeoutException)) {
                throw new DatabaseTimeoutException("The database did not respond in time (" + operation + ")", ex);
            }
            throw ex;
        }
    }

    private static String summarize(Object result) {
        if (result == null) {
            return "none";
        }
        if (result instanceof Optional<?> optional) {
            return optional.isPresent() ? "found" : "not found";
        }
        if (result instanceof Page<?> page) {
            return page.getNumberOfElements() + " of " + page.getTotalElements() + " rows";
        }
        if (result instanceof Collection<?> collection) {
            return collection.size() + " rows";
        }
        if (result instanceof Boolean || result instanceof Number) {
            return result.toString();
        }
        return result.getClass().getSimpleName();
    }
}
