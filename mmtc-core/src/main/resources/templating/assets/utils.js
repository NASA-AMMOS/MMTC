// roughly mirror utils.ts from webapp
function floorToMidnight(date) {
    let flooredDate = dateFns.setHours(date, 0);
    flooredDate = dateFns.setMinutes(flooredDate, 0);
    flooredDate = dateFns.setSeconds(flooredDate, 0);
    flooredDate = dateFns.setMilliseconds(flooredDate, 0);
    return flooredDate;
}

// just drop the timezone suffix
function toUtcIso8601WithDiscardedTimezone(date) {
    // return formatInTimeZone(date, 'UTC', "yyyy-DDD'T'HH:mm:ss.SSSSSS", { useAdditionalDayOfYearTokens: true });
    return dateFns.format(date, "yyyy-DDD'T'HH:mm:ss.SSSSSS", { useAdditionalDayOfYearTokens: true });
}

function toDoy(date) {
    return dateFns.format(date, "DDD", { useAdditionalDayOfYearTokens: true });
}

function toYyyyDddHhMm(date) {
    return dateFns.format(date, "yyyy-DDD HH:mm", { useAdditionalDayOfYearTokens: true });
}

function toYyyyDd(date) {
    return dateFns.format(date, "yyyy-DDD", { useAdditionalDayOfYearTokens: true });
}

function toHhMm(date) {
    return dateFns.format(date, "HH:mm", { useAdditionalDayOfYearTokens: true });
}

function toYyyyDddHhMmssSSS(date) {
    return dateFns.format(date, "yyyy-DDD HH:mm:ss.SSS", { useAdditionalDayOfYearTokens: true });
}

// todo rename this
function parseIso8601Utc(s) {
    return dateFns.parseISO(s);
    //return parseISO(s + "Z");
}

function getAttrTableHtmlFor(attrs) {
    let rethtml = "<div class='ml-5 mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-[11px] leading-tight pr-10'>";

    attrs.forEach(pair => {
        rethtml += `<div class='font-medium text-gray-500 tracking-wide'>${pair.key}</div>`;
        rethtml += `<div class='text-gray-900'>${pair.val}</div>`;
    })

    rethtml += "</div>"

    return rethtml;
}
