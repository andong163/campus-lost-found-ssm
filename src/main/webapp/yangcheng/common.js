/* 杨程 review/claim/audit 页面共用的小工具。所有动态文本使用 textContent，避免把接口数据当 HTML 执行。 */
(function () {
  'use strict';

  async function request(path, options = {}) {
    if (Cms.shell?.ready) await Cms.shell.ready;
    return Cms.api.request(path, {
      ...options,
      ...(options.body === undefined ? {} : {
        body: typeof options.body === 'string' ? JSON.parse(options.body) : options.body
      })
    });
  }

  function post(path, data) {
    return request(path, { method: 'POST', body: data });
  }

  window.addEventListener('cms:unauthorized', () => {
    document.getElementById('rows')?.replaceChildren();
    const detail = document.getElementById('detail');
    if (detail) detail.hidden = true;
  });

  if (Cms.shell?.ready) Cms.shell.ready.then(() => {
    let identity = Cms.shell.user?.id;
    window.addEventListener('cms:authchange', event => {
      const next = event.detail.user?.id;
      if (next === identity) return;
      identity = next;
      document.getElementById('rows')?.replaceChildren();
      document.querySelectorAll('form').forEach(form => form.reset());
      const detail = document.getElementById('detail');
      if (detail) detail.hidden = true;
      window.location.reload();
    });
  });

  function text(tag, value, className) {
    const element = document.createElement(tag);
    if (value !== undefined && value !== null) {
      element.textContent = String(value);
    }
    if (className) {
      element.className = className;
    }
    return element;
  }

  function itemId(id) {
    return text('span', '#' + id, 'item-id');
  }

  function itemCell(id, title) {
    const cell = text('td');
    cell.append(itemId(id), document.createTextNode(' ' + (title || '')));
    return cell;
  }

  function row(cells) {
    const tr = document.createElement('tr');
    cells.forEach(cell => tr.appendChild(cell instanceof Node ? cell : text('td', cell)));
    return tr;
  }

  function actionButton(label, className, handler) {
    const style = className === 'danger' ? 'button-danger' : 'button-primary';
    const button = text('button', label, 'button ' + style);
    button.type = 'button';
    button.addEventListener('click', handler);
    return button;
  }

  const statusStyles = new Map([
    ['FOUND', ['招领', 'positive']], ['LOST', ['寻物', 'outline']],
    ['PENDING', ['待核实', 'outline']], ['PENDING_REVIEW', ['待审核', 'outline']],
    ['APPROVED', ['已通过', 'solid']], ['PUBLISHED', ['已发布', 'solid']],
    ['OPEN', ['未结案', 'outline']], ['RESOLVED', ['已结案', 'positive']],
    ['COMPLETED', ['已完成', 'positive']], ['CANCELLED', ['已撤销', 'muted']],
    ['DRAFT', ['草稿', 'muted']], ['OFFLINE', ['已下架', 'muted']], ['REJECTED', ['已驳回', 'error']],
    ['SUCCESS', ['成功', 'solid']], ['FAILURE', ['失败', 'error']]
  ]);

  function badge(status) {
    const [label, style] = statusStyles.get(status) || [status, 'muted'];
    return text('span', label, 'cms-badge cms-badge-' + style);
  }

  function setMessage(id, message, kind) {
    const element = document.getElementById(id);
    if (!element) return;
    element.textContent = message || '';
    element.className = 'message ' + (kind || '');
  }

  function errorMessage(error) {
    if (!error) return '操作失败';
    if (error.status === 401) return '请先登录，再执行此操作。';
    if (error.status === 403) return '没有权限执行该操作。';
    return error.message || '操作失败';
  }

  function onAccountChange(handler) {
    let previous;
    function update(user) {
      const next = user ? user.id : null;
      if (next === previous) return;
      previous = next;
      handler(user);
    }
    window.addEventListener('cms:authchange', event => update(event.detail.user));
    Cms.shell.ready.then(() => update(Cms.shell.user));
  }

  function formatTime(value) {
    if (!value) return '';
    return String(value).replace('T', ' ').slice(0, 19);
  }

  window.YC = {request, post, text, row, itemId, itemCell, badge, actionButton, setMessage, errorMessage, formatTime, onAccountChange};
})();
