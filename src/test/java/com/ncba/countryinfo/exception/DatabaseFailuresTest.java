package com.ncba.countryinfo.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

class DatabaseFailuresTest {

    @Test
    void recognisesTimeoutsAtEveryLayer() {
        assertThat(DatabaseFailures.isTimeout(new QueryTimeoutException("query timeout"))).isTrue();
        assertThat(DatabaseFailures.isTimeout(new CannotAcquireLockException("Lock wait timeout exceeded"))).isTrue();
        assertThat(DatabaseFailures.isTimeout(new TransactionTimedOutException("deadline passed"))).isTrue();
        assertThat(DatabaseFailures.isTimeout(new CannotCreateTransactionException("no connection",
                new SQLTransientConnectionException("Connection is not available, request timed out")))).isTrue();
        assertThat(DatabaseFailures.isTimeout(new DataAccessResourceFailureException("read failed",
                new SocketTimeoutException("Read timed out")))).isTrue();
        assertThat(DatabaseFailures.isTimeout(new RuntimeException(new SQLTimeoutException("Statement cancelled")))).isTrue();
    }

    @Test
    void doesNotTreatOtherFailuresAsTimeouts() {
        assertThat(DatabaseFailures.isTimeout(new DataIntegrityViolationException("Duplicate entry"))).isFalse();
        assertThat(DatabaseFailures.isTimeout(new DataAccessResourceFailureException("Communications link failure"))).isFalse();
        assertThat(DatabaseFailures.isTimeout(new IllegalStateException("boom"))).isFalse();
        assertThat(DatabaseFailures.isTimeout(null)).isFalse();
    }
}
