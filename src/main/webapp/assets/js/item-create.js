(async function () {
  'use strict';
  const form = document.getElementById('form');
  const notice = document.getElementById('notice');
  const button = form.querySelector('[type=submit]');
  await Cms.shell.ready;
  if (!Cms.shell.user) {
    location.replace(Cms.shell.link('pages/auth/login.html?next=' +
      encodeURIComponent(location.pathname)));
    return;
  }
  if (!Cms.shell.hasPermission('item:create')) {
    notice.textContent = '当前账号没有新增信息的权限。';
    return;
  }
  const identity = Cms.shell.user.id;
  let active = true;
  window.addEventListener('cms:authchange', event => {
    if (event.detail.user?.id !== identity) {
      active = false;
      form.reset();
      form.hidden = true;
      button.disabled = true;
      location.reload();
    }
  });
  form.hidden = false;
  button.disabled = true;
  try {
    const categories = await Cms.api.request('/api/public/categories');
    if (!active) return;
    function add(nodes, depth = 0) {
      for (const category of nodes) {
        const option = document.createElement('option');
        option.value = category.id;
        option.textContent = '　'.repeat(depth) + category.name;
        form.elements.categoryId.append(option);
        add(category.children || [], depth + 1);
      }
    }
    add(categories);
    if (form.elements.categoryId.options.length === 1) {
      notice.textContent = '暂无可用分类，请联系管理人员添加分类后刷新。';
      return;
    }
    button.disabled = false;
  } catch (error) {
    notice.textContent = error.message;
    return;
  }
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (button.disabled || !active || Cms.shell.user?.id !== identity) return;
    button.disabled = true;
    notice.textContent = '正在保存草稿…';
    const values = Object.fromEntries(new FormData(form));
    values.categoryId = Number(values.categoryId);
    if (values.eventTime.length === 16) values.eventTime += ':00';
    try {
      const item = await Cms.api.request('/api/items', { method: 'POST', body: values });
      if (!active) return;
      location.assign(Cms.shell.link('pages/items/detail.html?id=' + encodeURIComponent(item.id)));
    } catch (error) {
      if (!active || error.name === 'AbortError') return;
      notice.textContent = error.message;
      button.disabled = false;
    }
  });
})();
