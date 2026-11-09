/* Local calendar dates only. Never pass through Date/toISOString or the browser's timezone. */
(function (root, factory) {
  'use strict';
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.PublicDateFilters = api;
})(typeof window === 'undefined' ? globalThis : window, function () {
  'use strict';
  function validDate(value) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    if (!match) return false;
    const year = Number(match[1]), month = Number(match[2]), day = Number(match[3]);
    const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
    const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    return year > 0 && month >= 1 && month <= 12 && day >= 1 && day <= days[month - 1];
  }
  function datePart(value) {
    const match = /^(\d{4}-\d{2}-\d{2})(?:$|[T ]\d{2}:\d{2})/.exec(value || '');
    return match && validDate(match[1]) ? match[1] : '';
  }
  function bounds(from = '', to = '') {
    if ((from && !validDate(from)) || (to && !validDate(to))) throw new RangeError('请选择有效的起始日期和截止日期。');
    if (from && to && from > to) throw new RangeError('起始日期不能晚于截止日期，请调整日期范围。');
    return {
      ...(from ? { from: from + 'T00:00:00' } : {}),
      ...(to ? { to: to + 'T23:59:59.999999999' } : {})
    };
  }
  function advancedConditions(values) {
    const enabled = [];
    if (values.from || values.to) enabled.push('日期');
    if (values.resolutionStatus) enabled.push('处理状态');
    if ((values.sortBy && values.sortBy !== 'publishedAt') || (values.direction && values.direction !== 'desc')) enabled.push('排序');
    return enabled;
  }
  return Object.freeze({ datePart, bounds, advancedConditions });
});
