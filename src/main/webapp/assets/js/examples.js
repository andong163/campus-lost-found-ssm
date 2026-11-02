(function (window, document) {
  'use strict';
  const Cms = window.Cms;
  const state = document.querySelector('#records-state'), body = document.querySelector('#records-body');
  const table = document.querySelector('#records-table'), pagerHost = document.querySelector('#records-pagination');
  let controller, sequence = 0;
  const pager = new Cms.Pagination(pagerHost, load);
  function clear(message) { body.replaceChildren(); table.hidden = true; pagerHost.hidden = true; state.hidden = false; state.textContent = message; }
  async function load(query = { page: 1, size: 5 }) {
    controller?.abort(); controller = new AbortController(); const current = ++sequence;
    if (!Cms.shell.user) { clear('请先登录后读取记录。'); return; }
    if (!Cms.shell.hasPermission('audit:view')) { clear('当前账号没有查看操作记录的权限。'); return; }
    clear('正在读取记录…');
    try {
      const page = await Cms.api.request(`/api/operation-logs?page=${query.page}&size=${query.size}`, { signal: controller.signal });
      if (current !== sequence) return;
      const pagination = Cms.Pagination.state(page);
      if (page.page > pagination.pages) return load({ page: pagination.pages, size: page.size });
      if (!Array.isArray(page.records)) throw new Cms.api.ApiError(502);
      body.replaceChildren();
      page.records.forEach(record => {
        const row = document.createElement('tr');
        [record.createdAt?.replace('T', ' '), record.operatorName || '—', record.action, record.result === 'SUCCESS' ? '成功' : '失败'].forEach(value => {
          const cell = document.createElement('td'); cell.textContent = String(value ?? '—'); row.append(cell);
        }); body.append(row);
      });
      table.hidden = page.records.length === 0; state.hidden = page.records.length !== 0; state.textContent = '暂无操作记录。';
      pagerHost.hidden = false; pager.render(page);
    } catch (error) {
      if (current !== sequence || error.name === 'AbortError') return;
      clear(error instanceof Cms.api.ApiError ? error.message : '暂时无法读取记录。');
    }
  }
  window.addEventListener('cms:authchange', () => { sequence++; controller?.abort(); clear('登录状态已更新。'); load(); });
  document.querySelector('#refresh-records').addEventListener('click', () => load({ page: pager.value.page, size: pager.value.size }));
  Cms.shell.ready.then(() => load());
})(window, document);
