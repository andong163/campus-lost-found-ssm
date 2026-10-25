const api = async (path, options = {}) => {
  const response = await fetch('../../api' + path, {credentials:'same-origin', ...options});
  const result = await response.json();
  if (!response.ok) throw new Error(result.message || '请求失败');
  return result.data;
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
