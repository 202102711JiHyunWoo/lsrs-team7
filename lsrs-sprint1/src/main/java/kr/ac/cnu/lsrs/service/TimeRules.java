package kr.ac.cnu.lsrs.service;
import java.time.*;

public final class TimeRules {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private TimeRules() { }
    public static void validate(Instant start, Instant end, int unitMinutes) {
        ZonedDateTime first = start.atZone(ZONE);
        ZonedDateTime last = end.atZone(ZONE);

        Instant dayEnd = first.toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZONE)
                .toInstant();

        if (!start.isBefore(end) || end.isAfter(dayEnd))
            throw BusinessException.bad(
                    "종료 시각은 시작보다 늦고, 시작 날짜의 24:00 이하여야 합니다.");

        if (first.getMinute() % unitMinutes != 0
                || last.getMinute() % unitMinutes != 0
                || first.getSecond() != 0
                || last.getSecond() != 0
                || first.getNano() != 0
                || last.getNano() != 0)
            throw BusinessException.bad(
                    unitMinutes + "분 단위로 시간을 선택해 주세요.");
    }
    public static boolean overlaps(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }
}
