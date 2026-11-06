package pl.piomin.services;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** The default Flyway locations include db/demo, which seeds fake data into an empty database. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DemoDataIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void seedsEmptyDatabaseOnceAndNeverTouchesExistingData() {
        assertThat(count("select count(*) from person")).isEqualTo(250);
        assertThat(count("select count(*) from task")).isEqualTo(2000);
        assertThat(count("select count(*) from task where assignee_id is not null")).isBetween(1, 1999);

        flyway.migrate();
        assertThat(count("select count(*) from person")).isEqualTo(250);
        assertThat(count("select count(*) from task")).isEqualTo(2000);

        jdbc.update("delete from task");
        jdbc.update("delete from person where id > (select min(id) from person)");
        flyway.migrate();
        assertThat(count("select count(*) from person")).isEqualTo(1);
        assertThat(count("select count(*) from task")).isZero();

        jdbc.update("delete from person");
        flyway.migrate();
        assertThat(count("select count(*) from person")).isEqualTo(250);
        assertThat(count("select count(*) from task")).isEqualTo(2000);
    }
}
