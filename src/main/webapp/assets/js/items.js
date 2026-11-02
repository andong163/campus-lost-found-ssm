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
  node.textContent=message; node.className=error?'error':'success';
};
const statusText={DRAFT:'草稿',PENDING_REVIEW:'待审核',APPROVED:'已批准',
  PUBLISHED:'已发布',REJECTED:'已驳回',OFFLINE:'已下架'};

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
  const form=document.getElementById('form');
  if (form && document.body.dataset.pageTitle!=='登录') form.hidden=true;
});
