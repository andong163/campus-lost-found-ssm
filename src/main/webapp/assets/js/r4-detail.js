/* YYX R4: existing detail surfaces, shared shell and request wrapper. */
(async function () {
  'use strict';
  const Cms=window.Cms, mode=document.body.dataset.r4Detail;
  if (!mode) return;
  const id=new URLSearchParams(location.search).get('id');
  const el=id=>document.getElementById(id);
  const text=(id,value)=>{ const node=el(id); if(node)node.textContent=value||''; };
  const resultText={OPEN:'未结案',LOST_RECOVERED:'作者已确认找回',FOUND_HANDOVER:'已完成实际交接',RESOLVED_UNCONFIRMED:'已结案（交接历史待核对）'};
  const reasons={LOGIN_REQUIRED:'登录后可查看认领资格',NO_PERMISSION:'当前账号没有认领权限',OWN_ITEM:'不能认领本人发布的信息',LOST_NOT_CLAIMABLE:'寻物信息不提供认领申请',RESOLVED:'已结案，保留历史查看',ACTIVE_APPLICATION:'你已有有效申请，请到我的认领查看',ALREADY_APPROVED:'已有核实通过的认领，等待实际交接',CATEGORY_UNAVAILABLE:'分类不可用',NOT_PUBLISHED:'信息尚未公开'};
  let item=null, epoch=0, pending=false;
  function clearPrivate() {
    ++epoch; text('r4-contact-value',''); text('r4-claim-message','');
    if(el('r4-claim-link'))el('r4-claim-link').hidden=true;
    el('r4-resolve')?.replaceChildren();
    el('r4-contact-button').disabled=false;
    pending=false;
  }
  async function eligibility() {
    const current=epoch;
    if(!item || mode!=='public') return;
    const link=el('r4-claim-link');link.hidden=true;
    if(item.resolutionStatus==='RESOLVED'){text('r4-claim-message',reasons.RESOLVED);return;}
    if(item.itemType==='LOST'){text('r4-claim-message',reasons.LOST_NOT_CLAIMABLE);return;}
    if(!Cms.shell.user){text('r4-claim-message',reasons.LOGIN_REQUIRED);return;}
    try {
      const value=await Cms.api.request('/api/items/'+id+'/claim-eligibility');
      if(current!==epoch)return;
      text('r4-claim-message',value.eligible?'请提交可供核实的物品特征。':reasons[value.reason]||'当前不可认领，请刷新信息。');
      if(value.eligible){link.href=Cms.shell.link('claim/mine.html?itemId='+encodeURIComponent(id));link.hidden=false;}
    } catch(error){if(current===epoch)text('r4-claim-message',error.message);}
  }
  async function history() {
    const current=epoch;
    try {
      const value=await Cms.api.request((mode==='public'?'/api/public/items/':'/api/items/')+id+'/resolution');
      if(current!==epoch)return;
      text('r4-history',(resultText[value.result]||'结案结果待核对')+(value.resolvedAt?' · 结案时间：'+value.resolvedAt.replace('T',' '):''));
    }catch(error){if(current===epoch)text('r4-history',error.message);}
  }
  async function ownerActions() {
    if(mode!=='owner' || !item || item.status!=='PUBLISHED' || item.resolutionStatus!=='OPEN')return;
    const current=epoch;
    const permission=item.itemType==='LOST'?'item:resolve:own':'claim:handover:own';
    if(!Cms.shell.hasPermission(permission))return;
    let claimId=null;
    if(item.itemType==='FOUND'){
      try {
        const context=await Cms.api.request('/api/items/'+id+'/handover');
        if(current!==epoch)return;
        if(!context.approvedClaimId){text('r4-resolve','尚无核实通过的认领，不能确认交接。');return;}
        claimId=context.approvedClaimId;
      }catch(error){if(current===epoch)text('r4-resolve',error.message);return;}
    }
    const button=document.createElement('button');button.type='button';
    button.textContent=item.itemType==='LOST'?'确认本人已找回':'确认已实际交接';
    button.onclick=async()=>{
      if(button.disabled || !window.confirm(item.itemType==='LOST'?'确认已经实际找回物品？':'确认已在线下将物品交给核实通过的认领人？'))return;
      button.disabled=true;
      try {
        await Cms.api.request('/api/items/'+id+'/resolve',{method:'POST',body:{version:item.version,claimId}});
        if(current===epoch)location.reload();
      }catch(error){if(current===epoch){text('notice',error.message);button.disabled=false;}}
    };
    if(current===epoch)el('r4-resolve').replaceChildren(button);
  }
  el('r4-contact-button').onclick=async()=>{
    if(pending)return;
    if(!Cms.shell.user){Cms.shell.openLogin();text('r4-contact-value','请先登录，再主动查看联系方式。');return;}
    const current=epoch;pending=true;el('r4-contact-button').disabled=true;
    try {
      const value=await Cms.api.request('/api/items/'+id+'/contact');
      if(current===epoch)text('r4-contact-value',value.contactInfo);
    }catch(error){if(current===epoch)text('r4-contact-value',error.message);}
    finally{if(current===epoch){pending=false;el('r4-contact-button').disabled=false;}}
  };
  let identity=Cms.shell.user?.id;
  window.addEventListener('cms:authchange',event=>{
    const next=event.detail.user?.id;
    if(next===identity)return;identity=next;clearPrivate();
    if(mode==='public')eligibility();
  });
  window.addEventListener('pagehide',clearPrivate);
  window.addEventListener('cms:r4item',event=>{item=event.detail;clearPrivate();history();ownerActions();});
  await Cms.shell.ready;
  if(!/^[1-9][0-9]*$/.test(id||'')){text('title','无效的信息 ID');el('r4-contact').hidden=true;return;}
  if(mode==='public'){
    try {
      item=await Cms.api.request('/api/public/items/'+encodeURIComponent(id));
      text('title',item.title);text('content',item.content);
      text('meta',`${item.itemType==='LOST'?'寻物':'招领'} · ${item.location} · ${item.eventTime} · ${item.resolutionStatus==='RESOLVED'?'已结案':'未结案'}`);
      await Promise.all([history(),eligibility()]);
    }catch(error){text('title','无法显示信息');text('notice',error.message);el('r4-contact').hidden=true;}
  }
})();
