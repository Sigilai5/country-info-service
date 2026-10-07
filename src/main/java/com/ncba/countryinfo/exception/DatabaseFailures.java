package com.ncba.countryinfo.exception;

import java.net.SocketTimeoutException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.TransactionTimedOutException;

/** Classifies database exceptions, which may arrive wrapped by Spring, Hibernate or the JDBC driver. */
public final class DatabaseFailures {

    private DatabaseFailures() {
    }

    /**
     * True for any kind of database timeout:
     * <ul>
     *   <li>query timeout (Statement.setQueryTimeout / jakarta.persistence.query.timeout)</li>
     *   <li>InnoDB lock wait timeout or deadlock (another transaction holds the row)</li>
     *   <li>Spring transaction timeout (spring.transaction.default-timeout)</li>
     *   <li>no connection available from the pool in time (HikariCP connection-timeout)</li>
     *   <li>socket read/connect timeout to MySQL</li>
     * </ul>
     */
    public static boolean isTimeout(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof DatabaseTimeoutException
                    || t instanceof QueryTimeoutException
                    || t instanceof TransactionTimedOutException
                    || t instanceof PessimisticLockingFailureException
                    || t instanceof jakarta.persistence.QueryTimeoutException
                    || t instanceof jakarta.persistence.LockTimeoutException
                    || t instanceof SQLTimeoutException
                    || t instanceof SQLTransientConnectionException
                    || t instanceof SocketTimeoutException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
