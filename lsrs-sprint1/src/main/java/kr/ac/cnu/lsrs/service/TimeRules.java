package kr.ac.cnu.lsrs.service;
import java.time.*;

public final class TimeRules {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private TimeRules() { }
    public static void validate(Instant start, Instant end, int unitMinutes) {
        ZonedDateTime first = start.atZone(ZONE); ZonedDateTime last = end.atZone(ZONE);
        if (!start.isBefore(end) || !first.toLocalDate().equals(last.toLocalDate()))
            throw BusinessException.bad("같은 날짜 안에서 시작보다 늦은 종료 시각을 선택해 주세요.");
        if (first.getMinute() % unitMinutes != 0 || last.getMinute() % unitMinutes != 0 ||
                first.getSecond() != 0 || last.getSecond() != 0 || first.getNano() != 0 || last.getNano() != 0)
            throw BusinessException.bad(unitMinutes + "분 단위로 시간을 선택해 주세요.");
    }
    public static boolean overlaps(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }
}
