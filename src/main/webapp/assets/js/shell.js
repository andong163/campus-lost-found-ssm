(function (window, document) {
  'use strict';
  const Cms = window.Cms;
  const root = new URL(Cms.api.root);
  let user = null, authSequence = 0;
  const modules = ['auth', 'category', 'items', 'review', 'claims', 'audit'];
  const labels = { auth: '账户管理', category: '分类管理', items: '我的信息', review: '审核工作台', claims: '认领与交接', audit: '操作记录' };
  const manifests = new Map();
  let host, dialog, feedback, sessionArea, nav, loginTrigger;
  const link = path => new URL(path, root).href;
  function node(tag, className, text) {
    const element = document.createElement(tag); if (className) element.className = className;
    if (text !== undefined) element.textContent = text; return element;
  }
  function notify(message, type = 'info') {
    feedback.textContent = message; feedback.className = `feedback ${type}`; feedback.hidden = !message;
  }
  function report(error) { if (error.name !== 'AbortError') notify(error instanceof Cms.api.ApiError ? error.message : '暂时无法完成操作，请稍后再试。', 'error'); }
  function setUser(value) {
    user = value; renderSession(); renderNav();
    window.dispatchEvent(new CustomEvent('cms:authchange', { detail: { user } }));
  }
  function hasPermission(code) { return !!user && (user.roles?.includes('ADMIN') || user.permissions?.includes(code)); }
  function renderSession() {
    sessionArea.replaceChildren();
    const identity = node('span', 'session-identity', user ? user.displayName : '欢迎来到校园失物招领');
    sessionArea.append(identity);
    const button = node('button', 'button ' + (user ? 'button-quiet' : 'button-primary'), user ? '退出登录' : '登录'); button.type = 'button';
    button.addEventListener('click', async () => {
      if (!user) { openLogin(); return; }
      button.disabled = true;
      try {
        await Cms.api.request('/api/auth/logout', { method: 'POST' });
        authSequence++; Cms.api.invalidateSession(); setUser(null); notify('已退出登录。');
      } catch (error) { report(error); } finally { button.disabled = false; }
    });
    sessionArea.append(button);
  }
  function entryAllowed(entry) {
    return !entry.permission || hasPermission(entry.permission);
  }
  function renderNav() {
    if (!nav) return;
    nav.replaceChildren();
    const home = node('a', 'nav-link', '校园首页'); home.href = link('pages/index.html');
    if (window.location.pathname === new URL(home.href).pathname) { home.classList.add('active'); home.setAttribute('aria-current', 'page'); }
    nav.append(home, node('p', 'nav-section', '校园服务'));
    modules.forEach(module => {
      const entries = manifests.get(module) || [];
      if (!entries.length) {
        const item = node('div', 'nav-link unavailable'); item.append(node('span', '', labels[module]), node('span', 'soon', '待开放')); nav.append(item); return;
      }
      entries.filter(entryAllowed).forEach(entry => {
        const item = node('a', 'nav-link', entry.label); item.href = link(entry.page);
        if (window.location.pathname === new URL(item.href).pathname) { item.classList.add('active'); item.setAttribute('aria-current', 'page'); }
        nav.append(item);
      });
    });
  }
  async function loadManifests() {
    await Promise.all(modules.map(async module => {
      try {
        const response = await window.fetch(link(`pages/${module}/module.json`), { credentials: 'same-origin', cache: 'no-cache', redirect: 'error' });
        if (!response.ok) return;
        const value = await response.json();
        if (value.module !== module || !Array.isArray(value.entries)) return;
        const prefix = `pages/${module}/`;
        manifests.set(module, value.entries.filter(entry => entry && typeof entry.label === 'string' && entry.label.length <= 30 &&
          typeof entry.page === 'string' && entry.page.startsWith(prefix) && /^pages\/[a-z]+\/[a-zA-Z0-9_\/-]+\.html$/.test(entry.page) &&
          (!entry.permission || typeof entry.permission === 'string')));
      } catch (_) { /* An unavailable manifest must not block the remaining modules. */ }
    }));
    renderNav();
  }
  function openLogin() {
    if (!dialog.open) { loginTrigger = document.activeElement; dialog.showModal(); }
    dialog.querySelector('input').focus();
  }
  async function refreshSession() {
    const sequence = ++authSequence;
    try {
      const value = await Cms.api.request('/api/auth/me');
      if (sequence === authSequence) setUser(value);
    } catch (error) {
      if (sequence !== authSequence) return;
      if (error.status === 401) setUser(null); else report(error);
    }
    return user;
  }
  function createLogin() {
    dialog = node('dialog', 'login-dialog'); dialog.setAttribute('aria-labelledby', 'session-title'); dialog.setAttribute('aria-describedby', 'session-description');
    const form = node('form');
    const title = node('h2', '', '登录校园账号'); title.id = 'session-title';
    const description = node('p', 'muted', '登录后使用你的校园服务。'); description.id = 'session-description';
    form.append(node('p', 'eyebrow', '欢迎回来'), title, description);
    [['username', '账号', 'text', 'username'], ['password', '密码', 'password', 'current-password']].forEach(([name, text, type, autocomplete]) => {
      const label = node('label', 'field-label', text); const input = node('input'); input.name = name; input.type = type;
      input.required = true; input.autocomplete = autocomplete; input.maxLength = name === 'password' ? 64 : 30;
      input.setAttribute('aria-describedby', 'session-error');
      label.append(input); form.append(label);
    });
    const errorText = node('p', 'form-error'); errorText.id = 'session-error'; errorText.setAttribute('role', 'alert');
    const actions = node('div', 'dialog-actions');
    const cancel = node('button', 'button button-quiet', '取消'); cancel.type = 'button'; cancel.addEventListener('click', () => dialog.close());
    const submit = node('button', 'button button-primary', '登录'); submit.type = 'submit';
    actions.append(cancel, submit); form.append(errorText, actions); dialog.append(form); document.body.append(dialog);
    function clearValidation() {
      errorText.textContent = '';
      form.querySelectorAll('input').forEach(input => input.removeAttribute('aria-invalid'));
    }
    form.addEventListener('input', clearValidation);
    dialog.addEventListener('close', () => {
      form.reset(); clearValidation();
      const target = loginTrigger?.isConnected && !loginTrigger.hidden && !loginTrigger.disabled ? loginTrigger : sessionArea.querySelector('button');
      target?.focus();
    });
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (submit.disabled) return;
      submit.disabled = true; form.setAttribute('aria-busy', 'true'); clearValidation();
      const data = new FormData(form);
      try {
        ++authSequence;
        const value = await Cms.api.request('/api/auth/login', { method: 'POST', body: { username: data.get('username'), password: data.get('password') } });
        Cms.api.invalidateSession(); setUser(value); dialog.close(); notify('登录成功。');
      } catch (error) {
        errorText.textContent = error.status === 401 ? '账号或密码不正确，请重新输入。' : error.message;
        if (error.status === 400 || error.status === 401) {
          form.querySelectorAll('input').forEach(input => input.setAttribute('aria-invalid', 'true'));
          if (dialog.open) form.elements.password.focus();
        }
      } finally { form.elements.password.value = ''; submit.disabled = false; form.setAttribute('aria-busy', 'false'); }
    });
  }
  function mount() {
    host = document.querySelector('[data-cms-shell]');
    if (!host) return;
    const sidebar = node('aside', 'sidebar');
    const brand = node('a', 'brand'); brand.href = link('pages/index.html');
    brand.append(node('span', 'brand-mark', '归'), node('span', 'brand-name', '校园失物招领'));
    nav = node('nav', 'main-nav'); nav.setAttribute('aria-label', '主导航');
    sidebar.append(brand, nav, node('p', 'sidebar-note', '让遗失有回应，让善意被看见。'));
    const work = node('div', 'workspace'); const top = node('header', 'topbar');
    const heading = node('div'); heading.append(node('span', 'eyebrow', 'CAMPUS LOST & FOUND'), node('p', 'page-heading', document.body.dataset.pageTitle || '校园首页'));
    sessionArea = node('div', 'session-area'); top.append(heading, sessionArea);
    feedback = node('div', 'feedback'); feedback.hidden = true; feedback.setAttribute('role', 'status'); feedback.setAttribute('aria-live', 'polite');
    const content = document.querySelector('main'); content.id = 'main-content'; content.tabIndex = -1;
    const skip = node('a', 'skip-link', '跳到主要内容'); skip.href = '#main-content';
    work.append(top, feedback, content, node('footer', 'footer', '校园失物招领 · 让每一份善意都有回音'));
    host.append(skip, sidebar, work);
    createLogin(); renderSession(); renderNav();
    document.querySelectorAll('[data-login]').forEach(el => el.addEventListener('click', openLogin));
    window.addEventListener('cms:unauthorized', () => { authSequence++; setUser(null); });
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refreshSession(); });
    window.addEventListener('pageshow', event => { if (event.persisted) refreshSession(); });
    loadManifests();
    Cms.shell.ready = refreshSession();
  }
  Cms.shell = { mount, notify, report, openLogin, refreshSession, hasPermission, link, get user() { return user; } };
  mount();
})(window, document);
