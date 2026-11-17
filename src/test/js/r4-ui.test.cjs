const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const assets=path.resolve(__dirname,'../../main/webapp/assets/js');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function harness({resolved=false,user={id:2},contact}={}) {
  const nodes=new Map();
  for(const id of ['title','content','meta','notice','r4-history','r4-contact','r4-contact-button','r4-contact-value','r4-claim-message','r4-claim-link','r4-resolve'])
    nodes.set(id,{textContent:'',hidden:id==='r4-claim-link',disabled:false,replaceChildren(){this.textContent='';}});
  const events=new Map(),calls=[];
  const listeners=(type,event)=>{for(const fn of events.get(type)||[])fn(event);};
  const Cms={shell:{user,ready:Promise.resolve(),hasPermission:()=>true,link:p=>'/cms/'+p,openLogin(){calls.push('LOGIN');}},
    api:{request:async(p)=>{
      calls.push(p);
      if(p.endsWith('/contact'))return contact?contact():{contactInfo:'<img src=x onerror=alert(1)>'};
      if(p.endsWith('/resolution'))return {result:resolved?'FOUND_HANDOVER':'OPEN',resolvedAt:resolved?'2026-11-03T10:00:00':null};
      if(p.endsWith('/claim-eligibility'))return {eligible:true,reason:'ELIGIBLE'};
      return {id:1,title:'<script>alert(1)</script>',content:'<img src=x>',location:'<b>地点</b>',itemType:'FOUND',resolutionStatus:resolved?'RESOLVED':'OPEN',eventTime:'2026-11-02'};
    }}};
  const win={Cms,addEventListener(type,fn){if(!events.has(type))events.set(type,[]);events.get(type).push(fn);}};
  const context={window:win,Cms,document:{body:{dataset:{r4Detail:'public'}},getElementById:id=>nodes.get(id)},location:{search:'?id=1',reload(){}},URLSearchParams,DOMException,Promise};
  vm.runInNewContext(fs.readFileSync(path.join(assets,'r4-detail.js'),'utf8'),context);
  return {nodes,calls,Cms,events,listeners};
}
test('detail reads contact only on deliberate click, and renders untrusted data as text',async()=>{
  const h=harness();await tick();
  assert.equal(h.calls.filter(x=>x.endsWith('/contact')).length,0);
  assert.equal(h.nodes.get('title').textContent,'<script>alert(1)</script>');
  assert.equal(h.nodes.get('r4-claim-link').hidden,false);
  await h.nodes.get('r4-contact-button').onclick();
  assert.equal(h.calls.filter(x=>x.endsWith('/contact')).length,1);
  assert.equal(h.nodes.get('r4-contact-value').textContent,'<img src=x onerror=alert(1)>');
});
test('resolved history disables new claim and keeps actual handover time visible',async()=>{
  const h=harness({resolved:true});await tick();
  assert.equal(h.nodes.get('r4-claim-link').hidden,true);
  assert.match(h.nodes.get('r4-history').textContent,/已完成实际交接.*2026-11-03/);
  assert.equal(h.calls.filter(x=>x.endsWith('/claim-eligibility')).length,0);
});
test('anonymous contact opens login without requesting private endpoint',async()=>{
  const h=harness({user:null});await tick();await h.nodes.get('r4-contact-button').onclick();
  assert.ok(h.calls.includes('LOGIN'));assert.equal(h.calls.filter(x=>x.endsWith('/contact')).length,0);
});
test('late contact response after logout cannot repopulate private text',async()=>{
  let finish;const h=harness({contact:()=>new Promise(resolve=>{finish=resolve;})});await tick();
  const pending=h.nodes.get('r4-contact-button').onclick();
  h.Cms.shell.user=null;h.listeners('cms:authchange',{detail:{user:null}});
  finish({contactInfo:'PRIVATE_LATE_CONTACT'});await pending;
  assert.equal(h.nodes.get('r4-contact-value').textContent,'');
});
test('claim adapter reloads after guest login and discards late responses on identity change',async()=>{
  const events={},calls=[],rows={replaceChildren(){calls.push('clear');}},form={reset(){calls.push('reset');}};
  let resolveRequest;
  const Cms={shell:{ready:Promise.resolve()},api:{request:()=>new Promise(resolve=>{resolveRequest=resolve;})}};
  const win={YC:{text(){}},addEventListener:(type,fn)=>events[type]=fn};
  vm.runInNewContext(fs.readFileSync(path.join(assets,'r4-claim-ui.js'),'utf8'),{
    window:win,Cms,DOMException,location:{reload(){calls.push('reload');}},document:{getElementById:id=>id==='rows'?rows:id==='applyForm'?form:null}});
  events['cms:authchange']({detail:{user:null}});
  events['cms:authchange']({detail:{user:{id:2}}});
  assert.equal(calls.filter(x=>x==='reload').length,1);
  const pending=win.YC.request('/api/claims/mine');await tick();
  events['cms:authchange']({detail:{user:null}});resolveRequest({records:[{claimDescription:'private'}]});
  await assert.rejects(pending,{name:'AbortError'});
});
