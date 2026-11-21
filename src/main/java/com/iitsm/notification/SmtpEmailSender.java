package com.iitsm.notification;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Real e-mail delivery with a minimal SMTP client over implicit TLS (port 465, e.g. Gmail), using only the JDK.
 * Sending runs on a background thread, so a slow or failing mail server never blocks incident handling.
 */
public class SmtpEmailSender implements EmailSender {
    private final String host, user, password, from, overrideTo;
    private final int port;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "smtp-sender"); t.setDaemon(true); return t; });

    /** @param overrideTo if not blank, every mail is redirected to this address (handy for demos with fake user e-mails) */
    public SmtpEmailSender(String host, int port, String user, String password, String overrideTo) {
        this.host = host; this.port = port; this.user = user; this.password = password; this.from = user;
        this.overrideTo = overrideTo == null || overrideTo.isBlank() ? null : overrideTo.trim();
    }

    @Override public void send(String to, String subject, String body) {
        String target = overrideTo != null ? overrideTo : to;
        worker.submit(() -> {
            try { deliver(target, subject, body); System.out.println("[email] sent to " + target + ": " + subject); }
            catch (IOException | RuntimeException e) { System.err.println("[email] delivery to " + target + " failed: " + e.getMessage()); }
        });
    }

    private void deliver(String to, String subject, String body) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
            socket.setSoTimeout(15_000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            expect(in, "220");
            command(out, in, "EHLO iitsm", "250");
            command(out, in, "AUTH LOGIN", "334");
            command(out, in, b64(user), "334");
            command(out, in, b64(password), "235");
            command(out, in, "MAIL FROM:<" + clean(from) + ">", "250");
            command(out, in, "RCPT TO:<" + clean(to) + ">", "250");
            command(out, in, "DATA", "354");
            String message = "From: I-ITSM <" + clean(from) + ">\r\n"
                    + "To: " + clean(to) + "\r\n"
                    + "Subject: =?UTF-8?B?" + b64(clean(subject)) + "?=\r\n"
                    + "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()) + "\r\n"
                    + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"
                    + Base64.getMimeEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8))   // base64 body: no dot-stuffing needed
                    + "\r\n.\r\n";
            out.write(message); out.flush();
            expect(in, "250");
            try { out.write("QUIT\r\n"); out.flush(); } catch (IOException ignored) { }
        }
    }

    private static void command(BufferedWriter out, BufferedReader in, String line, String expectedCode) throws IOException {
        out.write(line + "\r\n"); out.flush();
        expect(in, expectedCode);
    }

    /** Reads a (possibly multi-line) SMTP reply and checks its status code. */
    private static void expect(BufferedReader in, String code) throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null) throw new IOException("Mail server closed the connection");
            if (!line.startsWith(code)) throw new IOException("Unexpected SMTP reply: " + line);
        } while (line.length() > 3 && line.charAt(3) == '-');
    }

    private static String b64(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String clean(String s) { return s.replace("\r", "").replace("\n", "").replace("<", "").replace(">", ""); }   // prevents header injection
}
