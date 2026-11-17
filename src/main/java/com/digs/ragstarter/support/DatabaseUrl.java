package com.digs.ragstarter.support;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Converts postgres://user:pass@host:port/db (or postgresql://...) into JDBC settings. */
public record DatabaseUrl(String jdbcUrl, String username, String password) {

    public static Optional<DatabaseUrl> parse(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(url.strip());
            String scheme = uri.getScheme();
            if (!"postgres".equals(scheme) && !"postgresql".equals(scheme)) {
                return Optional.empty();
            }
            String userInfo = uri.getRawUserInfo();
            String user = "";
            String pass = "";
            if (userInfo != null) {
                int colon = userInfo.indexOf(':');
                user = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
                pass = colon < 0 ? "" : decode(userInfo.substring(colon + 1));
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getRawPath() == null || uri.getRawPath().length() <= 1 ? "/postgres" : uri.getRawPath();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + path + query;
            return Optional.of(new DatabaseUrl(jdbc, user, pass));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
