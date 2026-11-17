package com.digs.ragstarter.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseUrlTest {

    @Test
    void parsesRailwayStyleUrl() {
        DatabaseUrl db = DatabaseUrl.parse("postgresql://postgres:s3cr%40t@pgvector.railway.internal:5432/railway").orElseThrow();
        assertEquals("jdbc:postgresql://pgvector.railway.internal:5432/railway", db.jdbcUrl());
        assertEquals("postgres", db.username());
        assertEquals("s3cr@t", db.password());
    }

    @Test
    void defaultsPortAndDatabaseAndKeepsQuery() {
        DatabaseUrl db = DatabaseUrl.parse("postgres://u:p@host?sslmode=require").orElseThrow();
        assertEquals("jdbc:postgresql://host:5432/postgres?sslmode=require", db.jdbcUrl());
    }

    @Test
    void ignoresBlankAndForeignUrls() {
        assertTrue(DatabaseUrl.parse(null).isEmpty());
        assertTrue(DatabaseUrl.parse(" ").isEmpty());
        assertTrue(DatabaseUrl.parse("mysql://u:p@h/db").isEmpty());
        assertTrue(DatabaseUrl.parse("not a url").isEmpty());
    }
}
