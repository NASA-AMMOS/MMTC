import { format, setHours, setMinutes, setSeconds, setMilliseconds } from 'date-fns'

export function floorToMidnight(date: Date) {
  let flooredDate = setHours(date, 0);
  flooredDate = setMinutes(flooredDate, 0);
  flooredDate = setSeconds(flooredDate, 0);
  flooredDate = setMilliseconds(flooredDate, 0);
  return flooredDate;
}

// just drop the timezone suffix
export function toIso8601WithDiscardedTimezone(date: Date) {
  return format(date, "yyyy-DDD'T'HH:mm:ss.SSSSSS", { useAdditionalDayOfYearTokens: true });
}

export function toYyyDddHhMm(date: Date) {
  return format(date, "yyyy-DDD HH:mm", { useAdditionalDayOfYearTokens: true });
}
