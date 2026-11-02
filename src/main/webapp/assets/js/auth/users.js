/*
 * 用户管理页：分页查询、启停、角色分配与详情。
 * 所有请求走公共 Cms.api（自动解包 Result、自动附带 X-CSRF-Token），权限判断走 Cms.shell。
 */
(function () {
  'use strict';

  const REQUIRED = 'user:manage';
  const el = (id) => document.getElementById(id);
  const alertBox = el('alert');
  let roles = [];
  let canListRoles = false;
  let current = null;
  let pager = null;

  function showAlert(message, ok) {
    alertBox.textContent = message;
    alertBox.className = 'cms-alert is-visible ' + (ok ? 'is-ok' : 'is-error');
  }

  function clearAlert() {
    alertBox.className = 'cms-alert';
    alertBox.textContent = '';
  }

  function formatTime(value) {
    if (!value) return '—';
    return String(value).replace('T', ' ').slice(0, 19);
  }

  function query() {
    const form = el('filter-form');
    const params = new URLSearchParams();
    const username = form.username.value.trim();
    const displayName = form.displayName.value.trim();
    if (username) params.set('username', username);
    if (displayName) params.set('displayName', displayName);
    if (form.roleCode.value) params.set('roleCode', form.roleCode.value);
    if (form.enabled.value) params.set('enabled', form.enabled.value);
    return params;
  }

  async function loadPage(request) {
    clearAlert();
    const params = query();
    params.set('page', String(request.page));
    params.set('size', String(request.size));
    try {
      const result = await Cms.api.request('/api/users?' + params.toString());
      renderRows(result.records || []);
      el('empty').hidden = (result.records || []).length > 0;
      if (pager) pager.render({ page: result.page, size: result.size, total: result.total });
    } catch (error) {
      el('empty').hidden = true;
      el('rows').textContent = '';
      showAlert(error.message || '用户列表加载失败');
      if (error.status === 401) el('workspace').hidden = true;
    }
  }

  function renderRows(records) {
    const tbody = el('rows');
    tbody.textContent = '';
    for (const user of records) {
      const tr = document.createElement('tr');

      const name = document.createElement('td');
      name.textContent = user.username;
      tr.append(name);

      const display = document.createElement('td');
      display.textContent = user.displayName || '—';
      tr.append(display);

      const roleCell = document.createElement('td');
      if ((user.roles || []).length) {
        for (const role of user.roles) {
          const tag = document.createElement('span');
          tag.className = 'cms-tag';
          tag.textContent = role;
          roleCell.append(tag);
        }
      } else {
        roleCell.textContent = '—';
      }
      tr.append(roleCell);

      const state = document.createElement('td');
      const badge = document.createElement('span');
      badge.className = 'cms-tag ' + (user.enabled ? 'is-on' : 'is-off');
      badge.textContent = user.enabled ? '启用' : '停用';
      state.append(badge);
      tr.append(state);

      const created = document.createElement('td');
      created.textContent = formatTime(user.createdAt);
      tr.append(created);

      const actions = document.createElement('td');
      actions.className = 'actions';

      const detailBtn = document.createElement('button');
      detailBtn.type = 'button';
      detailBtn.className = 'cms-btn-ghost';
      detailBtn.textContent = '详情';
      detailBtn.addEventListener('click', () => openDetail(user.id));
      actions.append(detailBtn);

      const toggleBtn = document.createElement('button');
      toggleBtn.type = 'button';
      toggleBtn.className = user.enabled ? 'cms-btn-danger' : 'cms-btn-primary';
      toggleBtn.textContent = user.enabled ? '停用' : '启用';
      toggleBtn.addEventListener('click', () => toggleStatus(user, toggleBtn));
      actions.append(toggleBtn);

      const roleBtn = document.createElement('button');
      roleBtn.type = 'button';
      roleBtn.className = 'cms-btn-ghost';
      roleBtn.textContent = '分配角色';
      roleBtn.addEventListener('click', () => openRoleModal(user));
      actions.append(roleBtn);

      tr.append(actions);
      tbody.append(tr);
    }
  }

  async function toggleStatus(user, button) {
    const next = !user.enabled;
    const verb = next ? '启用' : '停用';
    if (!window.confirm('确认' + verb + '用户「' + user.username + '」？')) return;
    button.disabled = true;
    try {
      await Cms.api.request('/api/users/' + user.id + '/status', {
        method: 'PUT',
        body: { enabled: next }
      });
      showAlert('已' + verb + '用户「' + user.username + '」', true);
      await loadPage(pager ? pager.value : { page: 1, size: 10 });
    } catch (error) {
      showAlert(error.message || (verb + '失败'));
      button.disabled = false;
    }
  }

  async function openDetail(id) {
    try {
      const user = await Cms.api.request('/api/users/' + id);
      el('d-username').textContent = user.username;
      el('d-display').textContent = user.displayName || '—';
      el('d-enabled').textContent = user.enabled ? '启用' : '停用';
      el('d-roles').textContent = (user.roles || []).join('、') || '—';
      el('d-permissions').textContent = (user.permissions || []).join('、') || '—';
      el('d-created').textContent = formatTime(user.createdAt);
      el('detail').hidden = false;
    } catch (error) {
      showAlert(error.message || '详情加载失败');
    }
  }

  function openRoleModal(user) {
    current = user;
    el('role-modal-target').textContent = '用户：' + user.username + '（' + (user.displayName || '—') + '）';
    el('role-options').textContent = '';
    if (canListRoles) {
      el('role-manual-hint').hidden = true;
      el('role-manual').hidden = true;
      for (const role of roles) {
        const label = document.createElement('label');
        const box = document.createElement('input');
        box.type = 'checkbox';
        box.value = role.code;
        box.checked = (user.roles || []).includes(role.code);
        label.append(box, document.createTextNode(role.name ? role.name + '（' + role.code + '）' : role.code));
        el('role-options').append(label);
      }
    } else {
      el('role-manual-hint').hidden = false;
      el('role-manual').hidden = false;
      el('role-codes').value = (user.roles || []).join(',');
    }
    el('role-modal').hidden = false;
    el('role-save').disabled = false;
  }

  function selectedRoles() {
    if (!canListRoles) {
      return el('role-codes').value.split(',').map((item) => item.trim()).filter(Boolean);
    }
    return Array.from(el('role-options').querySelectorAll('input[type=checkbox]'))
      .filter((box) => box.checked)
      .map((box) => box.value);
  }

  async function saveRoles() {
    if (!current) return;
    const codes = selectedRoles();
    if (!codes.length) {
      showAlert('至少需要保留一个角色');
      return;
    }
    el('role-save').disabled = true;
    try {
      await Cms.api.request('/api/users/' + current.id + '/roles', {
        method: 'PUT',
        body: { roleCodes: codes }
      });
      el('role-modal').hidden = true;
      showAlert('已更新用户「' + current.username + '」的角色', true);
      await loadPage(pager ? pager.value : { page: 1, size: 10 });
    } catch (error) {
      showAlert(error.message || '角色分配失败');
      el('role-save').disabled = false;
    }
  }

  async function loadRoles() {
    if (!Cms.shell.hasPermission('role:manage')) return;
    try {
      roles = await Cms.api.request('/api/roles');
      canListRoles = true;
      const select = el('f-role');
      for (const role of roles) {
        const option = document.createElement('option');
        option.value = role.code;
        option.textContent = role.name || role.code;
        select.append(option);
      }
    } catch (error) {
      // 没有角色管理权限时只能手工填写角色代码，页面其余功能不受影响。
      canListRoles = false;
    }
  }

  async function start() {
    await Cms.shell.ready;
    if (!Cms.shell.user) {
      window.location.replace(Cms.shell.link('pages/auth/login.html?next=' +
        encodeURIComponent(window.location.pathname + window.location.search)));
      return;
    }
    if (!Cms.shell.hasPermission(REQUIRED)) {
      el('denied').hidden = false;
      return;
    }
    el('workspace').hidden = false;
    await loadRoles();
    pager = new Cms.Pagination(el('pager'), loadPage);
    el('filter-form').addEventListener('submit', function (event) {
      event.preventDefault();
      loadPage({ page: 1, size: pager ? pager.value.size : 10 });
    });
    el('filter-form').addEventListener('reset', function () {
      window.setTimeout(() => loadPage({ page: 1, size: pager ? pager.value.size : 10 }), 0);
    });
    el('d-close').addEventListener('click', function () { el('detail').hidden = true; });
    el('role-save').addEventListener('click', saveRoles);
    el('role-cancel').addEventListener('click', function () { el('role-modal').hidden = true; });
    await loadPage({ page: 1, size: 10 });
  }

  window.addEventListener('cms:authchange', function (event) {
    if (!event.detail || !event.detail.user) {
      el('workspace').hidden = true;
      el('denied').hidden = true;
    }
  });

  start();
})();
