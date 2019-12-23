package com.scoder.jusic.service.imp;

import com.scoder.jusic.configuration.JusicProperties;
import com.scoder.jusic.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * @author H
 */
@Service
@Slf4j
public class MailServiceImpl implements MailService {

    @Autowired
    private JusicProperties jusicProperties;
    @Autowired
    private JavaMailSender javaMailSender;

    @Override
    public boolean sendSimpleMail(String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(jusicProperties.getMailSendFrom());
        message.setTo(jusicProperties.getMailSendTo());
        message.setSubject(subject);
        message.setText(content);

        try {
            javaMailSender.send(message);
            return true;
        } catch (Exception e) {
            log.info("邮件发送异常: {}", e.getMessage());
            return false;
        }
    }

}
