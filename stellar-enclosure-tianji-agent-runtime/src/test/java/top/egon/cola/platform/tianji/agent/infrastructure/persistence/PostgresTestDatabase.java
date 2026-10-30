package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.concurrent.atomic.AtomicLong;

/** One disposable test database per Maven test JVM. */
final class PostgresTestDatabase {
    private static final AtomicLong TENANT = new AtomicLong(10_000);
    private static DriverManagerDataSource dataSource;

    private PostgresTestDatabase() {
    }

    static synchronized DriverManagerDataSource dataSource() {
        if (dataSource != null) {
            return dataSource;
        }
        String url = System.getProperty("tianji.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("integration tests require an explicit loopback tianji_it_ database");
        }
        DriverManagerDataSource candidate = new DriverManagerDataSource();
        candidate.setUrl(url);
        JdbcTemplate jdbc = new JdbcTemplate(candidate);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname = 'tianji_agent'", Integer.class) != 0) {
            throw new IllegalStateException("integration tests require a fresh database without a Tianji schema");
        }
        Flyway flyway = Flyway.configure().dataSource(candidate).schemas("tianji_agent")
                .defaultSchema("tianji_agent").locations("classpath:db/tianji-agent").target("9")
                .cleanDisabled(true).load();
        flyway.migrate();
        flyway.validate();
        dataSource = candidate;
        return dataSource;
    }

    static long nextTenant() {
        return TENANT.incrementAndGet();
    }
}
