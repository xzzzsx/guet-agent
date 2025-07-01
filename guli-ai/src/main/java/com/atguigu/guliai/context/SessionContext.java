package com.atguigu.guliai.context;

public class SessionContext {
    private static final ThreadLocal<Long> SESSION_ID_HOLDER = new ThreadLocal<>();

    public static void setCurrentSessionId(Long sessionId) {
        SESSION_ID_HOLDER.set(sessionId);
    }

    public static Long getCurrentSessionId() {
        return SESSION_ID_HOLDER.get();
    }

    public static void clear() {
        SESSION_ID_HOLDER.remove();
    }
}