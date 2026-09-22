package com.cavedream.server.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送（QQ SMTP）。未配置授权码时 configured()=false，
 * 接口层据此返回友好提示，配合 dev 主码仍可开发。
 */
@Service
public class MailService {

    private final JavaMailSender sender;
    private final String from;
    private final String authCode;

    public MailService(JavaMailSender sender,
                       @Value("${spring.mail.username:}") String from,
                       @Value("${spring.mail.password:}") String authCode) {
        this.sender = sender;
        this.from = from;
        this.authCode = authCode;
    }

    /** 授权码是否已配置（未配时接口层提示改用 dev 主码）。 */
    public boolean configured() {
        return from != null && !from.isBlank()
                && authCode != null && !authCode.isBlank()
                && !authCode.startsWith("REPLACE_WITH");
    }

    /** 发送验证码邮件（HTML 简单模板）。 */
    public void sendCode(String to, String code) throws Exception {
        MimeMessage msg = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject("[CaveDream 梦迹行者] 邮箱验证码");
        helper.setText("<div style='font-family:sans-serif'>"
                + "<h3>你的入梦验证码</h3>"
                + "<p style='font-size:28px;letter-spacing:6px;font-weight:bold;color:#4a5ae8'>" + code + "</p>"
                + "<p>5 分钟内有效。如果不是你本人操作，请忽略本邮件。</p>"
                + "</div>", true);
        sender.send(msg);
    }
}
