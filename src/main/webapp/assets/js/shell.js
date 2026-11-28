(function (window, document) {
  'use strict';
  const Cms = window.Cms;
  const root = new URL(Cms.api.root);
  let user = null, authSequence = 0;
  const modules = ['auth', 'category', 'items', 'review', 'claims', 'audit'];
  const primaryPages = [
    ['pages/items/public.html', '寻物招领'],
    ['pages/items/create.html', '发布信息'],
    ['claim/mine.html', '我的认领']
  ];
  const personalPages = new Set(['pages/items/mine.html', 'claim/handover.html']);
  const aliases = {
    'pages/items/public.html': ['pages/items/public-detail.html'],
    'pages/items/mine.html': ['pages/items/detail.html', 'pages/items/edit.html'],
    'pages/review/pending.html': ['review/review.html']
  };
  const manifests = new Map();
  const modulePages = {
    claims: ['claim/mine.html', 'claim/manage.html', 'claim/handover.html'],
    audit: ['audit/logs.html']
  };
  let host, dialog, feedback, sessionArea, nav, loginTrigger, navMenu;
  const link = path => {
    const destination = new URL(path, root);
    if (destination.pathname.endsWith('.html')) destination.searchParams.set('ui', 'soft-20261128-3');
    return destination.href;
  };
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
    if (user) sessionArea.append(node('span', 'session-identity', user.displayName || user.username));
    const button = node('button', 'button ' + (user ? 'button-quiet' : 'button-primary'), user ? '退出登录' : '登录 / 注册'); button.type = 'button';
    button.addEventListener('click', async () => {
      if (!user) { openLogin(); return; }
      button.disabled = true;
      try {
        await Cms.api.request('/api/auth/logout', { method: 'POST' });
        authSequence++; setUser(null); notify('已退出登录。');
      } catch (error) { report(error); } finally { button.disabled = false; }
    });
    sessionArea.append(button);
  }
  function entryAllowed(entry) {
    return (!entry.authenticated || !!user) && (!entry.permission || hasPermission(entry.permission));
  }
  function navLink(page, label, className) {
    const item = node('a', className, label);
    item.href = link(page);
    const current = window.location.pathname;
    const exact = current === new URL(item.href).pathname;
    if (exact || aliases[page]?.some(alias => current === new URL(link(alias)).pathname)) {
      item.classList.add('active');
      item.setAttribute('aria-current', exact ? 'page' : 'location');
    }
    return item;
  }

  function renderNav() {
    if (!nav) return;
    const wasOpen = navMenu?.open && !!user;
    const focusMenu = navMenu?.contains(document.activeElement);
    navMenu = null;
    nav.replaceChildren(navLink('pages/index.html', '校园首页', 'nav-link'));
    const entries = modules.flatMap(module => manifests.get(module) || []).filter(entryAllowed);
    const primary = new Set(primaryPages.map(([page]) => page));
    for (const [page, label] of primaryPages) {
      if (entries.some(entry => entry.page === page)) nav.append(navLink(page, label, 'nav-link'));
    }
    const secondary = entries.filter(entry => !primary.has(entry.page));
    if (!secondary.length) return;
    navMenu = node('details', 'nav-menu');
    const summary = node('summary', 'nav-link', secondary.some(entry => !personalPages.has(entry.page)) ? '工作台' : '我的服务');
    const dropdown = node('div', 'nav-menu-content');
    for (const entry of secondary) {
      const item = navLink(entry.page, entry.label, 'nav-menu-link');
      if (item.getAttribute('aria-current')) summary.classList.add('active');
      dropdown.append(item);
    }
    navMenu.open = !!wasOpen;
    navMenu.append(summary, dropdown);
    nav.append(navMenu);
    if (focusMenu && wasOpen) summary.focus();
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
          typeof entry.page === 'string' &&
          ((entry.page.startsWith(prefix) && /^pages\/[a-z]+\/[a-zA-Z0-9_\/-]+\.html$/.test(entry.page)) ||
            modulePages[module]?.includes(entry.page)) &&
          (entry.authenticated === undefined || typeof entry.authenticated === 'boolean') &&
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
    const description = node('p', 'muted', '请输入账号和密码。'); description.id = 'session-description';
    form.append(title, description);
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
    const register = node('a', 'register-link', '没有账号？去注册');
    register.href = link('pages/auth/login.html#register');
    actions.append(cancel, submit); form.append(errorText, actions);
    form.append(register); dialog.append(form); document.body.append(dialog);
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
        setUser(value); dialog.close(); notify('登录成功。');
      } catch (error) {
        errorText.textContent = error.status === 401 ? '账号或密码不正确，请重新输入。' : error.message;
        if (error.status === 400 || error.status === 401) {
          form.querySelectorAll('input').forEach(input => input.setAttribute('aria-invalid', 'true'));
          if (dialog.open) form.elements.password.focus();
        }
      } finally { form.elements.password.value = ''; submit.disabled = false; form.setAttribute('aria-busy', 'false'); }
    });
  }
  function brandMark() {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('class', 'brand-mark');
    svg.setAttribute('viewBox', '0 0 32 32');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    const tag = document.createElementNS(svg.namespaceURI, 'path');
    tag.setAttribute('d', 'M7 9.5A3.5 3.5 0 0 1 10.5 6h8.2a3 3 0 0 1 2.1.9l5.3 5.3a3 3 0 0 1 0 4.2l-9.7 9.7a3 3 0 0 1-4.2 0l-5.3-5.3A3 3 0 0 1 6 18.7z');
    const hole = document.createElementNS(svg.namespaceURI, 'circle');
    hole.setAttribute('cx', '13');
    hole.setAttribute('cy', '12');
    hole.setAttribute('r', '1.8');
    svg.append(tag, hole);
    return svg;
  }

  function mount() {
    host = document.querySelector('[data-cms-shell]');
    if (!host) return;
    const brand = node('a', 'brand'); brand.href = link('pages/index.html');
    brand.append(brandMark(), node('span', 'brand-name', '校园失物招领'));
    nav = node('nav', 'main-nav'); nav.setAttribute('aria-label', '主导航');
    const work = node('div', 'workspace');
    const top = node('header', 'topbar');
    const inner = node('div', 'topbar-inner');
    sessionArea = node('div', 'session-area');
    inner.append(brand, nav, sessionArea);
    top.append(inner);
    feedback = node('div', 'feedback'); feedback.hidden = true; feedback.setAttribute('role', 'status'); feedback.setAttribute('aria-live', 'polite');
    const content = document.querySelector('main'); content.id = 'main-content'; content.tabIndex = -1;
    const skip = node('a', 'skip-link', '跳到主要内容'); skip.href = '#main-content';
    work.append(top, feedback, content);
    host.append(skip, work);
    createLogin(); renderSession(); renderNav();
    document.querySelectorAll('[data-login]').forEach(el => el.addEventListener('click', openLogin));
    window.addEventListener('cms:unauthorized', () => { authSequence++; setUser(null); });
    document.addEventListener('click', event => {
      if (navMenu?.open && !navMenu.contains(event.target)) navMenu.open = false;
    });
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && navMenu?.open) {
        navMenu.open = false;
        navMenu.querySelector('summary').focus();
      }
    });
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refreshSession(); });
    window.addEventListener('pageshow', event => { if (event.persisted) refreshSession(); });
    loadManifests();
    Cms.shell.ready = refreshSession();
  }
  Cms.shell = { mount, notify, report, openLogin, refreshSession, hasPermission, link, get user() { return user; } };
  mount();
})(window, document);
