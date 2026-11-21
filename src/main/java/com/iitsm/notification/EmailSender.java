package com.iitsm.notification;

/** Contract for anything that can deliver an e-mail (real SMTP, a mock in tests, ...). */
public interface EmailSender {
    void send(String to, String subject, String body);
}
