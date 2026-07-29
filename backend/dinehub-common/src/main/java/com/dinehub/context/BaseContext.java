package com.dinehub.context;

public class BaseContext {

    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE_HOLDER = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
        ROLE_HOLDER.remove();
    }

    public static void setCurrentRole(String role) {
        ROLE_HOLDER.set(role);
    }

    public static String getCurrentRole() {
        return ROLE_HOLDER.get();
    }

}
