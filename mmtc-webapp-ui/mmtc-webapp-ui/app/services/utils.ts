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

export function toYyyyDddHhMm(date: Date) {
  return format(date, "yyyy-DDD HH:mm", { useAdditionalDayOfYearTokens: true });
}

export function toYyyyDd(date: Date) {
  return format(date, "yyyy-DDD", { useAdditionalDayOfYearTokens: true });
}

export function toHhMm(date: Date) {
  return format(date, "HH:mm", { useAdditionalDayOfYearTokens: true });
}
