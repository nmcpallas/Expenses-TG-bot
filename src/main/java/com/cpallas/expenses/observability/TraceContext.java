package com.cpallas.expenses.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/** Correlates logs for one user operation without collecting user data. */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER_NAME = "X-Trace-Id";

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[0-9a-f]{32}");

    private TraceContext() {
    }

    public static TraceScope open() {
        return open(MDC.get(MDC_KEY));
    }

    public static TraceScope open(String candidateTraceId) {
        String previousTraceId = MDC.get(MDC_KEY);
        String traceId = isValid(candidateTraceId) ? candidateTraceId : newTraceId();
        MDC.put(MDC_KEY, traceId);
        return new TraceScope(previousTraceId, traceId);
    }

    public static String currentTraceId() {
        String currentTraceId = MDC.get(MDC_KEY);
        return isValid(currentTraceId) ? currentTraceId : newTraceId();
    }

    private static boolean isValid(String traceId) {
        return traceId != null && TRACE_ID_PATTERN.matcher(traceId).matches();
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static final class TraceScope implements AutoCloseable {
        private final String previousTraceId;
        private final String traceId;

        private TraceScope(String previousTraceId, String traceId) {
            this.previousTraceId = previousTraceId;
            this.traceId = traceId;
        }

        public String traceId() {
            return traceId;
        }

        @Override
        public void close() {
            if (previousTraceId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousTraceId);
            }
        }
    }
}
