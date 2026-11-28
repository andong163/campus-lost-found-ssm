const api = async (path, options = {}) => {
  // Deferred shared assets execute after parsing; this API is first awaited by each page's loader.
  if (!window.Cms?.shell) await new Promise(resolve => document.addEventListener('DOMContentLoaded',resolve,{once:true}));
  await Cms.shell.ready;
  const identity = Cms.shell.user?.id;
  const result = await Cms.api.request('/api'+path,{method:options.method||'GET',
    ...(options.body===undefined?{}:{body:JSON.parse(options.body)})});
  if (!path.startsWith('/auth/') && identity !== Cms.shell.user?.id)
    throw new DOMException('会话已切换，请重新读取信息。','AbortError');
  return result;
};
const csrf = async () => api('/auth/csrf');
const write = async (path, method, data) => api(path, {
  method, headers: {'Content-Type':'application/json','X-CSRF-Token':await csrf()},
  body:JSON.stringify(data)
});
const show = (message, error = false) => {
  const node=document.querySelector('#notice');
  node.textContent=message;
  node.classList.toggle('error', error);
  node.classList.toggle('success', Boolean(message) && !error);
};
const statusText={DRAFT:'草稿',PENDING_REVIEW:'待审核',APPROVED:'已批准',
  PUBLISHED:'已发布',REJECTED:'已驳回',OFFLINE:'已下架'};

const itemUi = {
  node(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text != null) element.textContent = String(text);
    return element;
  },
  link(label, href, tone = 'quiet') {
    const link = itemUi.node('a', 'button button-' + tone, label);
    link.href = href;
    return link;
  },
  button(label, tone = 'quiet') {
    const button = itemUi.node('button', 'button button-' + tone, label);
    button.type = 'button';
    return button;
  },
  badge(status, label = statusText[status] || status) {
    const tone = {DRAFT:'muted', PENDING_REVIEW:'solid', APPROVED:'positive',
      PUBLISHED:'positive', REJECTED:'error', OFFLINE:'muted'}[status] || 'muted';
    return itemUi.node('span', 'cms-badge cms-badge-' + tone, label);
  },
  time(value) {
    const text = Cms.itemView.timeText(value);
    if (text === '—') return document.createTextNode(text);
    const time = itemUi.node('time', '', text);
    time.dateTime = value;
    return time;
  },
  fact(label, value) {
    const fact = itemUi.node('div');
    const desc = itemUi.node('dd');
    desc.append(value);
    fact.append(itemUi.node('dt', '', label), desc);
    return fact;
  },
  canEdit(item) {
    return ['DRAFT','REJECTED'].includes(item.status) && Cms.shell.hasPermission('item:edit:own');
  },
  canHandover(item) {
    return item.status === 'PUBLISHED' && item.resolutionStatus === 'OPEN'
      && Cms.shell.hasPermission('claim:handover:own');
  },
  handoverLink(id) {
    return itemUi.link('去交接结案', Cms.shell.link('claim/handover.html?itemId=' + encodeURIComponent(id)));
  },
  ownerCard(item, index) {
    const card = itemUi.node('article', 'panel record-card');
    card.style.setProperty('--item-order', index);
    const labels = itemUi.node('div', 'record-labels');
    labels.append(Cms.itemView.idBadge(item.id), itemUi.badge(item.status));
    const name = itemUi.node('div', 'record-name');
    name.append(labels, itemUi.node('h2', '', item.title));
    const facts = itemUi.node('dl', 'record-facts');
    facts.append(itemUi.fact('版本', String(item.version ?? '—')), itemUi.fact('更新时间', itemUi.time(item.updatedAt)));
    const id = encodeURIComponent(item.id);
    const actions = itemUi.node('div', 'record-actions');
    actions.append(itemUi.link('查看详情', 'detail.html?id=' + id));
    if (itemUi.canEdit(item)) actions.append(itemUi.link('编辑', 'edit.html?id=' + id));
    if (itemUi.canHandover(item)) actions.append(itemUi.handoverLink(item.id));
    const side = itemUi.node('div', 'record-side');
    side.append(facts, actions);
    card.append(name, side);
    return card;
  }
};

let moduleSessionInitialized = false, moduleIdentity = null;
window.addEventListener('cms:authchange', event => {
  const identity = event.detail.user?.id;
  if (event.detail.user) {
    if (moduleSessionInitialized && identity !== moduleIdentity) location.reload();
    moduleSessionInitialized = true; moduleIdentity = identity; return;
  }
  moduleSessionInitialized = true; moduleIdentity = null;
  for (const id of ['list','reviews','actions','title','state','content','meta']) {
    const node=document.getElementById(id); if (node) node.replaceChildren();
  }
  const detail = document.getElementById('owner-detail');
  if (detail) detail.hidden = true;
  const form=document.getElementById('form');
  if (form && document.body.dataset.pageTitle!=='登录') form.hidden=true;
});
