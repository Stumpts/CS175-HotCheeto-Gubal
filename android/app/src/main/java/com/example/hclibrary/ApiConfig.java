package com.example.hclibrary;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * Resolves and caches the reachable backend base URL once per app session.
 * Tries {@link BuildConfig#BASE_URL} first, and falls back to the Android emulator
 * host alias ({@code http://10.0.2.2:<port>}) if the configured URL is unreachable.
 */
public final class ApiConfig {

    private static final String EMULATOR_HOST = "10.0.2.2";
    private static final String DEFAULT_EMULATOR_URL = "http://10.0.2.2:8080";
    private static final int PROBE_TIMEOUT_MS = 1000;

    private static final Object LOCK = new Object();
    private static volatile String resolvedBaseUrl;

    private ApiConfig() {
    }

    /**
     * Returns the resolved base URL (no trailing slash). Performs reachability
     * detection at most once per app session once a reachable server is found.
     * Must be called from a background thread.
     */
    public static String getBaseUrl() {
        String cached = resolvedBaseUrl;
        if (cached != null) {
            return cached;
        }

        synchronized (LOCK) {
            if (resolvedBaseUrl != null) {
                return resolvedBaseUrl;
            }

            String primary = normalize(BuildConfig.BASE_URL);
            String fallback = buildEmulatorFallbackUrl(primary);

            if (primary.equalsIgnoreCase(fallback)) {
                resolvedBaseUrl = primary;
                return resolvedBaseUrl;
            }

            if (isReachable(primary)) {
                resolvedBaseUrl = primary;
                return resolvedBaseUrl;
            }

            if (isReachable(fallback)) {
                resolvedBaseUrl = fallback;
                return resolvedBaseUrl;
            }

            // Neither endpoint responded yet (e.g. backend not started); return primary
            // without caching so a subsequent request can resolve once the server is up.
            return primary;
        }
    }

    private static String normalize(String url) {
        if (url == null || url.trim().isEmpty()) {
            return DEFAULT_EMULATOR_URL;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String buildEmulatorFallbackUrl(String primaryUrl) {
        try {
            URI uri = URI.create(primaryUrl);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            int port = uri.getPort();
            if (port > 0) {
                return scheme + "://" + EMULATOR_HOST + ":" + port;
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_EMULATOR_URL;
    }

    private static boolean isReachable(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return false;
            }
            int port = uri.getPort();
            if (port <= 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }
}
