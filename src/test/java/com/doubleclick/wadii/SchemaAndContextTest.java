package com.doubleclick.wadii;

import com.doubleclick.wadii.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Flyway builds the schema and Hibernate validates every entity against it (ddl-auto=validate). */
class SchemaAndContextTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void schemaIsMigratedAndValidated() {
        Integer version = jdbc.queryForObject("SELECT MAX(CAST(version AS INT)) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(version).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'uq_response_one_accepted_per_order'", Integer.class))
                .isEqualTo(1);
    }
}
