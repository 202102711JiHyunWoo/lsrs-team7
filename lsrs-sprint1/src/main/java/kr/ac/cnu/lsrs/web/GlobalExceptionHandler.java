package kr.ac.cnu.lsrs.web;

import kr.ac.cnu.lsrs.service.BusinessException;
import kr.ac.cnu.lsrs.web.ApiDtos.ErrorView;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private ResponseEntity<ErrorView> error(HttpStatus status, String code, String message,
                                           Map<String, String> fields, Map<String, Object> details) {
        return ResponseEntity.status(status).body(new ErrorView(code, message, fields, details, UUID.randomUUID().toString()));
    }
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorView> business(BusinessException exception) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage(), Map.of(), exception.getDetails());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorView> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력한 정보를 확인해 주세요.", fields, Map.of());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorView> invalidInput(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "날짜·시간·필수 입력값의 형식을 확인해 주세요.", Map.of(), Map.of());
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorView> integrity(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "이미 등록되었거나 다른 요청이 먼저 처리한 정보입니다. 새로 확인해 주세요.", Map.of(), Map.of());
    }
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorView> lockFailure(PessimisticLockingFailureException exception) {
        return error(HttpStatus.CONFLICT, "RETRYABLE_CONFLICT", "다른 요청을 처리 중입니다. 최신 현황을 확인한 후 다시 시도해 주세요.", Map.of(), Map.of());
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorView> missing(NoResourceFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 페이지를 찾을 수 없습니다.", Map.of(), Map.of());
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorView> method(HttpRequestMethodNotSupportedException exception) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.", Map.of(), Map.of());
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorView> unexpected(Exception exception) {
        String requestId = UUID.randomUUID().toString();
        LOG.error("Unhandled request error {}", requestId, exception);
        return ResponseEntity.status(500).body(new ErrorView("INTERNAL_ERROR", "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", Map.of(), Map.of(), requestId));
    }
}
