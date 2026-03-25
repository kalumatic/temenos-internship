package com.temenos.temenosinternship.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

/**
 * Custom repository implementation for atomic timer operations.
 */
@Repository
public class TimerCustomRepositoryImpl implements TimerCustomRepository {

    private final DatabaseClient databaseClient;

    /**
     * Creates a custom timer repository implementation.
     *
     * @param databaseClient reactive database client
     */
    public TimerCustomRepositoryImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    /**
     * Returns the underlying reactive database client.
     *
     * @return reactive database client
     */
    public DatabaseClient getDatabaseClient() {
        return databaseClient;
    }
}
