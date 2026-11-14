package kr.ac.cnu.lsrs.service;

public interface MailGateway {
    String sendVerification(String email, String token);
}
