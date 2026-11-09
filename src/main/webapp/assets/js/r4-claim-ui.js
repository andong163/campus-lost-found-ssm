/* Reuse YC forms and safe text helpers; all network/session handling uses GMY's shared components. */
(function () {
  'use strict';
  let epoch=0,identity,initialized=false;
  const original=window.YC;
  async function request(path,options={}) {
    await Cms.shell.ready;
    const current=epoch;
    const value=await Cms.api.request(path,options);
    if(current!==epoch)throw new DOMException('会话已切换','AbortError');
    return value;
  }
  window.YC={...original,request,post:(path,body)=>request(path,{method:'POST',body}),
    errorMessage:error=>error.name==='AbortError'?'会话已切换，请刷新页面。':error.message};
  window.addEventListener('cms:authchange',event=>{
    const next=event.detail.user?.id;
    if(!initialized){identity=next;initialized=true;return;}
    if(next===identity)return;
    const wasLoggedIn=identity!==undefined;identity=next;++epoch;
    document.getElementById('rows')?.replaceChildren();
    if(wasLoggedIn || !next)document.getElementById('claimDescription')?.closest('form')?.reset();
    location.reload();
  });
  window.addEventListener('pagehide',()=>{
    ++epoch;document.getElementById('rows')?.replaceChildren();
    document.getElementById('applyForm')?.reset();
  });
})();
