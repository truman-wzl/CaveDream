package com.cavedream.core.net;

/** 后端服务地址（唯一来源）；可用系统属性 -Dcavedream.server=... 覆盖，便于换环境。 */
public final class ServerConfig {

    public static final String BASE_URL =
            System.getProperty("cavedream.server", "http://localhost:8081");

    private ServerConfig() {
    }
}
