package kr.ac.cnu.lsrs.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import kr.ac.cnu.lsrs.config.KeyMaterial;
import kr.ac.cnu.lsrs.domain.Seat;
import kr.ac.cnu.lsrs.repository.SeatRepository;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

@Service
public class QrService {
    private final KeyMaterial keys;
    private final SeatRepository seats;
    public QrService(KeyMaterial keys, SeatRepository seats) { this.keys = keys; this.seats = seats; }
    public String token(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(keys.bytes(), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("LSRS-SEAT-QR-v1:" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) { throw new IllegalStateException(exception); }
    }
    public byte[] png(long seatId) {
        Seat seat = seats.findById(seatId).orElseThrow(BusinessException::missing);
        String secret = token(seat.getCode());
        if (!TokenHash.matches(secret, seat.getQrTokenHash())) throw new IllegalStateException("Seat QR key does not match persisted data.");
        try {
            var matrix = new QRCodeWriter().encode("LSRS1|" + seat.getCode() + "|" + secret, BarcodeFormat.QR_CODE, 360, 360,
                    Map.of(EncodeHintType.MARGIN, 4, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output); return output.toByteArray();
        } catch (WriterException | IOException exception) { throw new IllegalStateException("Could not render seat QR", exception); }
    }
}
