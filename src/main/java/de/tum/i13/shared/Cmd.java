package de.tum.i13.shared;

import java.util.HashMap;
import java.util.Map;

public enum Cmd {
    PUT("put"), GET("get"), DELETE("delete"), KEYRANGE("keyrange"), KEYRANGE_READ("keyrange_read"),

    PUT_SUCCESS("put_success"), PUT_UPDATE("put_update"), PUT_ERROR("put_error"),

    GET_ERROR("get_error"), GET_SUCCESS("get_success"),

    DELETE_SUCCESS("delete_success"), DELETE_ERROR("delete_error"),

    KEYRANGE_SUCCESS("keyrange_success"), KEYRANGE_READ_SUCCESS("keyrange_read_success"),

    SERVER_STOPPED("server_stopped"), SERVER_NOT_RESPONSIBLE("server_not_responsible"),
    SERVER_WRITE_LOCK("server_write_lock"),

    ERROR("error");

    private String cmd;

    Cmd(String cmd) {
        this.cmd = cmd;
    }

    public String getCmd() {
        return cmd;
    }

    private static final Map<String, Cmd> lookup = new HashMap<>();
    static {
        for (Cmd env : Cmd.values()) {
            lookup.put(env.getCmd(), env);
        }
    }

    public static Cmd get(String url) {
        if (lookup.containsKey(url)) {
            return lookup.get(url);
        } else {
            return ERROR;
        }
    }

}
