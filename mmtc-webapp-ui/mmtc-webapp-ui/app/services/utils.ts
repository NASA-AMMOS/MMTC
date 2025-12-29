import { format, setHours, setMinutes, setSeconds, setMilliseconds } from 'date-fns'
import { parseISO, isBefore } from 'date-fns'
import { formatInTimeZone } from 'date-fns-tz'
import {parseDate, today, CalendarDate, getLocalTimeZone } from '@internationalized/date'

export function floorToMidnight(date: Date) {
  let flooredDate = setHours(date, 0);
  flooredDate = setMinutes(flooredDate, 0);
  flooredDate = setSeconds(flooredDate, 0);
  flooredDate = setMilliseconds(flooredDate, 0);
  return flooredDate;
}

export class UnifiedCalendarDateRange {
  beginCalendarDate: CalendarDate;
  beginDate: Date;
  beginYear: number;
  beginDoy: number;

  endCalendarDate: CalendarDate;
  endDate: Date;
  endYear: number;
  endDoy: number;

  constructor() {
    this.updateBeginWithCalendarDate(today('UTC'));
    this.updateEndWithCalendarDate(today('UTC'));
  }

  updateBeginWithCalendarDate(newBegin: CalendarDate) {
    this.beginCalendarDate = newBegin;
    this.beginDate = new Date(newBegin.year, newBegin.month - 1, newBegin.day, 0, 0, 0, 0); // newBegin.toDate('UTC');
    this.beginYear = newBegin.year;
    this.beginDoy = parseInt(toDoy(this.beginDate));
  }

  updateEndWithCalendarDate(newEnd: CalendarDate) {
    this.endCalendarDate = newEnd;
    this.endDate = new Date(newEnd.year, newEnd.month - 1, newEnd.day, 0, 0, 0, 0);
    this.endYear = newEnd.year;
    this.endDoy = parseInt(toDoy(this.endDate));
  }

  updateBeginWithDate(newBegin: Date) {
    this.updateBeginWithCalendarDate(
      new CalendarDate(newBegin.getUTCFullYear(), newBegin.getUTCMonth(), newBegin.getUTCDate())
    );
  }

  updateEndWithDate(newEnd: Date) {
    this.updateEndWithCalendarDate(
      new CalendarDate(newEnd.getUTCFullYear(), newEnd.getUTCMonth(), newEnd.getUTCDate())
    );
  }

  updateBeginWithYearDoy(newBeginYear: number, newBeginDoy: number) {
    updateBeginWithDate(parseIso8601Utc(`${newBeginYear}-${newBeginDoy}T00:00:00`))
  }

  updateEndWithYearDoy(newEndYear: number, newEndDoy: number) {
    updateEndWithDate(parseIso8601Utc(`${newEndYear}-${newEndDoy}T00:00:00`))
  }

  getCopy() {
    const newCopy = new UnifiedCalendarDateRange();
    newCopy.updateBeginWithCalendarDate(this.beginCalendarDate);
    newCopy.updateEndWithCalendarDate(this.endCalendarDate);
    return newCopy;
  }
}

// just drop the timezone suffix
export function toUtcIso8601WithDiscardedTimezone(date: Date) {
  return formatInTimeZone(date, 'UTC', "yyyy-DDD'T'HH:mm:ss.SSSSSS", { useAdditionalDayOfYearTokens: true });
  // return format(date, "yyyy-DDD'T'HH:mm:ss.SSSSSS", { useAdditionalDayOfYearTokens: true });
}

export function calendarDateToDoy(date: CalendarDate) {
  return toDoy(date.toDate(getLocalTimeZone()));
}

export function toDoy(date: Date) {
  return format(date, "DDD", { useAdditionalDayOfYearTokens: true });
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

export function toYyyyDddHhMmssSSS(date: Date) {
  return format(date, "yyyy-DDD HH:mm:ss.SSS", { useAdditionalDayOfYearTokens: true });
}

// todo rename this
export function parseIso8601Utc(s: string) {
  return parseISO(s);
  //return parseISO(s + "Z");
}

export function getAttrTableHtmlFor(attrs: []) {
  let rethtml = "<div class='ml-5 mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-[11px] leading-tight pr-10'>";

  attrs.forEach(pair => {
    rethtml += `<div class='font-medium text-gray-500 tracking-wide'>${pair.key}</div>`;
    rethtml += `<div class='text-gray-900'>${pair.val}</div>`;
  })

  rethtml += "</div>"

  return rethtml;
}
