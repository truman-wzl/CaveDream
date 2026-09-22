package com.cavedream.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CaveDream 后端服务入口：账号 / 云存档 / 同步日志。
 * 游戏客户端（desktop/core）只通过 HTTP API 访问本服务，MySQL 凭据永不下发到客户端。
 */
@SpringBootApplication
public class CaveDreamServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaveDreamServerApplication.class, args);
    }
}
