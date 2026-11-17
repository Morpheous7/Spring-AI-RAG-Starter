package com.digs.ragstarter;

import com.digs.ragstarter.support.DatabaseUrl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RagStarterApplication {

    public static void main(String[] args) {
        // Railway Postgres services expose PGHOST etc.; some expose only DATABASE_URL.
        // If PGHOST is missing, translate DATABASE_URL into Spring's datasource settings.
        if (isBlank(System.getenv("PGHOST")) && System.getProperty("spring.datasource.url") == null) {
            DatabaseUrl.parse(System.getenv("DATABASE_URL")).ifPresent(db -> {
                System.setProperty("spring.datasource.url", db.jdbcUrl());
                System.setProperty("spring.datasource.username", db.username());
                System.setProperty("spring.datasource.password", db.password());
            });
        }
        SpringApplication.run(RagStarterApplication.class, args);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
