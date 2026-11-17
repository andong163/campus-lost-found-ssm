const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/assets/js/category-select.js'), 'utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));

function mount(fetch, options, withApi = true) {
  class Element {
    constructor() { this.children = []; this.options = this.children; this.value = ''; this.disabled = false; }
    appendChild(child) { this.children.push(child); }
    remove(index) { this.children.splice(index, 1); }
    removeAttribute(name) { delete this[name]; }
    addEventListener() {}
  }
  const document = { currentScript: { src: 'http://localhost:8097/cms/assets/js/category-select.js' }, createElement: () => new Element() };
  const window = { document, Cms: withApi ? { api: { root: 'http://localhost:8097/cms/' } } : {} };
  vm.runInNewContext(source, { window, document, URL, fetch, console: { error() {} } });
  return window.Cms.CategorySelect.mount(new Element(), options);
}
const ok = () => ({ ok: true, json: async () => ({ data: [{ id: 1, name: '证件', children: [{ id: 2, name: '校园卡' }] }] }) });

test('default category endpoint follows the deployed app root, with or without api.js', async () => {
  for (const withApi of [true, false]) {
    const urls = [];
    const picker = mount(async url => { urls.push(url); return ok(); }, {}, withApi);
    await tick();
    assert.match(urls[0], /^\/cms\/api\/public\/categories\?t=\d+$/);
    assert.equal(picker.element.options.length, 3);
    picker.setValue(2); assert.equal(picker.getValue(), 2);
  }
});

test('an explicit empty or alternate context path remains supported', async () => {
  for (const base of ['', '/campus/']) {
    const urls = [];
    mount(async url => { urls.push(url); return ok(); }, { contextPath: base });
    await tick();
    assert.ok(urls[0].startsWith(base.replace(/\/$/, '') + '/api/public/categories?'));
  }
});

test('a failed category load can recover by reloading instead of remaining disabled', async () => {
  let failed = true;
  const picker = mount(async () => failed ? { ok: false, status: 503 } : ok(), {});
  await tick();
  assert.equal(picker.element.disabled, true);
  assert.match(picker.element.title, /503/);
  failed = false;
  await picker.reload();
  assert.equal(picker.element.disabled, false);
  assert.equal(picker.element.title, undefined);
  assert.equal(picker.element.options.length, 3);
});
