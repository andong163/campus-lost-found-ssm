const test = require('node:test');
const assert = require('node:assert/strict');
const dates = require('../../main/webapp/assets/js/public-date-filters.js');

test('empty and one-sided dates omit only the empty bound', () => {
  assert.deepEqual(dates.bounds('', ''), {});
  assert.deepEqual(dates.bounds('2026-11-03', ''), { from: '2026-11-03T00:00:00' });
  assert.deepEqual(dates.bounds('', '2026-11-03'), { to: '2026-11-03T23:59:59.999999999' });
});
test('same day includes its complete final second, with all nine fractional digits', () => {
  assert.deepEqual(dates.bounds('2026-11-03', '2026-11-03'), {
    from: '2026-11-03T00:00:00', to: '2026-11-03T23:59:59.999999999'
  });
});
test('cross-day and cross-year ranges keep the requested calendar boundaries', () => {
  assert.deepEqual(dates.bounds('2026-12-31', '2027-01-01'), {
    from: '2026-12-31T00:00:00', to: '2027-01-01T23:59:59.999999999'
  });
});
test('inverted dates are rejected while leap-day validity is respected', () => {
  assert.throws(() => dates.bounds('2026-11-04', '2026-11-03'), /起始日期不能晚于截止日期/);
  assert.throws(() => dates.bounds('2026-02-29', ''), /有效/);
  assert.equal(dates.bounds('2028-02-29', '').from, '2028-02-29T00:00:00');
});
test('old datetime URLs restore only the calendar date and submit the new whole-day semantics', () => {
  const start = dates.datePart('2026-11-01T14:30:20');
  const end = dates.datePart('2026-11-03T15:00:00.123456789');
  assert.deepEqual(dates.bounds(start, end), {
    from: '2026-11-01T00:00:00', to: '2026-11-03T23:59:59.999999999'
  });
  assert.equal(dates.datePart('2026-11-03'), '2026-11-03');
});
test('empty or invalid URL dates cannot become normalized neighboring dates', () => {
  assert.equal(dates.datePart(null), '');
  assert.equal(dates.datePart('2026-02-31T08:00'), '');
  assert.equal(dates.datePart('not-a-date'), '');
});
test('boundaries stay identical across host timezones', () => {
  const previous = process.env.TZ;
  try {
    for (const zone of ['UTC', 'Asia/Shanghai', 'Pacific/Kiritimati', 'America/Los_Angeles']) {
      process.env.TZ = zone;
      assert.deepEqual(dates.bounds('2026-11-03', '2026-11-03'), {
        from: '2026-11-03T00:00:00', to: '2026-11-03T23:59:59.999999999'
      });
    }
  } finally { if (previous === undefined) delete process.env.TZ; else process.env.TZ = previous; }
});
test('advanced-condition hint counts only dates, selected status or non-default sorting', () => {
  assert.deepEqual(dates.advancedConditions({sortBy:'publishedAt', direction:'desc'}), []);
  assert.deepEqual(dates.advancedConditions({from:'2026-11-03'}), ['日期']);
  assert.deepEqual(dates.advancedConditions({to:'2026-11-03',resolutionStatus:'OPEN',sortBy:'eventTime',direction:'asc'}), ['日期','处理状态','排序']);
});
