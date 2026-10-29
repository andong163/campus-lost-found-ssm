/* 杨程 review/claim/audit 页面共用的小工具。所有动态文本使用 textContent，避免把接口数据当 HTML 执行。 */
(function () {
  'use strict';

  function contextPath() {
    return location.pathname.replace(/\/(review|claim|audit)\/[^/]*$/, '') || '';
  }

  async function request(path, options) {
    const init = Object.assign({
      credentials: 'same-origin',
      headers: { 'Accept': 'application/json' }
    }, options || {});
    const response = await fetch(contextPath() + path, init);
    const raw = await response.text();
    let body = null;
    if (raw) {
      try { body = JSON.parse(raw); } catch (ignore) { body = null; }
    }
    if (!response.ok) {
      const error = new Error((body && body.message) || ('请求失败（' + response.status + '）'));
      error.status = response.status;
      throw error;
    }
    return body ? body.data : null;
  }

  function post(path, data) {
    return request(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify(data)
    });
  }

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

  function row(cells) {
    const tr = document.createElement('tr');
    cells.forEach(cell => tr.appendChild(cell instanceof Node ? cell : text('td', cell)));
    return tr;
  }

  function actionButton(label, className, handler) {
    const button = text('button', label, className || '');
    button.type = 'button';
    button.addEventListener('click', handler);
    return button;
  }

  function setMessage(id, message, kind) {
    const element = document.getElementById(id);
    if (!element) return;
    element.textContent = message || '';
    element.className = 'message ' + (kind || '');
  }

  function errorMessage(error) {
    if (!error) return '操作失败';
    if (error.status === 401) return '未登录或会话已失效，请先登录（登录页由身份模块提供）。';
    if (error.status === 403) return '没有权限执行该操作。';
    return error.message || '操作失败';
  }

  function formatTime(value) {
    if (!value) return '';
    return String(value).replace('T', ' ').slice(0, 19);
  }

  window.YC = {request, post, text, row, actionButton, setMessage, errorMessage, formatTime};
})();
