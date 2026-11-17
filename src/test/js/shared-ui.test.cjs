const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assets = path.resolve(__dirname, '../../main/webapp/assets/js');
function runtime(fetch) {
  const window = new EventTarget();
  Object.assign(window, { fetch, setTimeout, clearTimeout, document: { currentScript: { src:'http://localhost:9876/cms/assets/js/api.js' } } });
  const context = { window, URL, Headers, AbortController, DOMException, CustomEvent: class extends Event { constructor(type, value) { super(type); this.detail=value?.detail; } } };
  for (const file of ['api.js','pagination.js']) vm.runInNewContext(fs.readFileSync(path.join(assets,file),'utf8'),context,{ filename:file });
  return window;
}
const ok = data => new Response(JSON.stringify({ code:200, message:'success', data }), { status:200 });
const token = 'test-token-with-at-least-32-characters';

for (const reason of ['cancel', 'timeout']) test(`a write can ${reason} while waiting for CSRF without cancelling other callers`, {timeout:2000}, async () => {
  let finish, tokenSignal, tokenCalls=0, writes=0;
  const w=runtime((url,options)=>{
    if(url.endsWith('/csrf')) {tokenCalls++;tokenSignal=options.signal;return new Promise(resolve=>{finish=resolve;});}
    writes++;return Promise.resolve(ok({id:2}));
  });
  const abort=new AbortController();
  const stopped=w.Cms.api.request('/api/items',{method:'POST',body:{},signal:abort.signal,timeout:reason==='timeout'?10:1000});
  const survivor=w.Cms.api.request('/api/items',{method:'POST',body:{},timeout:1000});
  if(reason==='cancel')abort.abort();
  await assert.rejects(stopped,e=>reason==='cancel'?e.name==='AbortError':e.status===504);
  assert.equal(tokenSignal.aborted,false);assert.equal(tokenCalls,1);assert.equal(writes,0);
  finish(ok(token));assert.equal((await survivor).id,2);assert.equal(writes,1);
});

function minePage() {
  class Element { constructor(){this.children=[];this.value='';this.textContent='';this.disabled=false;} replaceChildren(){this.children=[];this.textContent='';} append(...nodes){this.children.push(...nodes);} }
  const elements=new Map(), requests=[], errors=[];let ready;
  const el=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id);};
  const document={querySelector:el,createElement:()=>new Element(),createTextNode:text=>({textContent:text}),addEventListener:(name,fn)=>{if(name==='DOMContentLoaded')ready=fn;}};
  const window=new EventTarget();
  const file=path.resolve(assets,'../../pages/items/mine.html');
  const inline=[...fs.readFileSync(file,'utf8').matchAll(/<script>([\s\S]*?)<\/script>/g)][0][1];
  vm.runInNewContext(inline,{window,document,Cms:{shell:{ready:Promise.resolve()}},api:url=>new Promise((resolve,reject)=>requests.push({url,resolve,reject})),statusText:{},show:message=>errors.push(message)});
  return {el,requests,errors,window,start:()=>ready()};
}
const mineResult=(title,total=30)=>({total,records:[{id:1,title,status:'DRAFT',version:0}]});
for(const oldFails of [false,true])test(`mine keeps the latest filter when an older request ${oldFails?'fails':'succeeds'} last`,async()=>{
  const p=minePage();await p.start();
  assert.equal(p.el('#next').disabled,true);
  p.el('#next').onclick();assert.equal(p.requests.length,1);
  p.el('#status').value='PUBLISHED';p.el('#status').onchange();
  p.requests[1].resolve(mineResult('latest'));await new Promise(setImmediate);
  if(oldFails)p.requests[0].reject(new Error('stale-error'));else p.requests[0].resolve(mineResult('old'));
  await new Promise(setImmediate);
  assert.equal(p.el('#list').children[0].children[0].textContent,'latest');
  assert.equal(p.errors.length,0);assert.equal(p.el('#next').disabled,false);
});
test('mine invalidates pending data on logout but keeps data for same-user refresh',async()=>{
  const p=minePage();
  const auth=user=>{const event=new Event('cms:authchange');event.detail={user};p.window.dispatchEvent(event);};
  auth({id:1});await p.start();p.requests[0].resolve(mineResult('current'));await new Promise(setImmediate);
  auth({id:1});assert.equal(p.el('#list').children[0].children[0].textContent,'current');
  p.el('#status').onchange();auth(null);p.requests[1].resolve(mineResult('private-old'));await new Promise(setImmediate);
  assert.equal(p.el('#list').children.length,0);assert.equal(p.el('#page').textContent,'');assert.equal(p.el('#next').disabled,true);
});

test('context path and same-origin credentials survive deeply nested pages', async () => {
  let call; const w=runtime(async (...args)=>{ call=args; return ok({ id:1 }); });
  const result=await w.Cms.api.request('/api/auth/me');
  assert.equal(result.id,1); assert.equal(call[0],'http://localhost:9876/cms/api/auth/me');
  assert.equal(call[1].credentials,'same-origin'); assert.equal(call[1].headers.get('X-CSRF-Token'),null);
});
test('foreign origins, traversal and non-API destinations never reach fetch', async () => {
  let calls=0; const w=runtime(async ()=>{ calls++; return ok(null); });
  for(const url of ['https://evil.invalid/api/x','//evil.invalid/api/x','/api/../../private','/api/../other','/assets/file','/api/\\evil'])
    await assert.rejects(w.Cms.api.request(url), error=>error.status===400);
  assert.equal(calls,0);
});
test('parallel writes share a session token request and attach the actual data string', async () => {
  const calls=[]; const w=runtime(async (url,options)=>{ calls.push([url,options]); return ok(url.endsWith('/csrf')?token:{ id:2 }); });
  await Promise.all([w.Cms.api.request('/api/items',{ method:'POST',body:{ title:'test' } }), w.Cms.api.request('/api/items',{ method:'POST',body:{ title:'other' } })]);
  assert.equal(calls.filter(([url])=>url.endsWith('/csrf')).length,1);
  for(const [,options] of calls.filter(([,options])=>options.method==='POST')) assert.equal(options.headers.get('X-CSRF-Token'),token);
});
test('login is possible without an anonymous CSRF endpoint', async () => {
  let calls=0; const w=runtime(async (url,options)=>{ calls++; assert.ok(url.endsWith('/login')); assert.equal(options.headers.get('X-CSRF-Token'),null); return ok({ id:1 }); });
  await w.Cms.api.request('/api/auth/login',{ method:'POST', body:{ username:'test',password:'test-only' } }); assert.equal(calls,1);
});
for(const status of [400,401,403,409,503]) test(`HTTP ${status} cannot expose SQL, HTML or server messages`, async ()=>{
  const w=runtime(async ()=>new Response('<script>private-sql-marker jdbc:secret</script>',{ status }));
  await assert.rejects(w.Cms.api.request('/api/auth/me'),error=>error.status===status && !/script|jdbc:|private-sql-marker/.test(error.message));
});
test('writes are never automatically retried after a conflict', async () => {
  let writes=0; const w=runtime(async url=>url.endsWith('/csrf')?ok(token):(writes++,new Response('',{status:409})));
  await assert.rejects(w.Cms.api.request('/api/items',{method:'POST',body:{}}),error=>error.status===409); assert.equal(writes,1);
});
test('invalidating a session clears tokens and an old pending token cannot cross sessions', async () => {
  let finish; const w=runtime(()=>new Promise(resolve=>{ finish=resolve; }));
  const pending=w.Cms.api.csrf(); w.Cms.api.invalidateSession(); finish(ok(token));
  await assert.rejects(pending,error=>error.status===401);
  let calls=0; w.fetch=async ()=>{ calls++; return ok(token+'-new'); };
  assert.equal(await w.Cms.api.csrf(),token+'-new'); assert.equal(calls,1);
});
test('401 emits an expiry event without accepting the response as data', async () => {
  const w=runtime(async ()=>new Response('',{status:401})); let events=0; w.addEventListener('cms:unauthorized',()=>events++);
  await assert.rejects(w.Cms.api.request('/api/auth/me')); assert.equal(events,1);
});
test('malformed success bodies and mismatched response codes are safe errors', async () => {
  const w=runtime(async ()=>new Response('not-json',{status:200}));
  await assert.rejects(w.Cms.api.request('/api/x'),error=>error.status===502);
  w.fetch=async ()=>ok(undefined); await assert.rejects(w.Cms.api.request('/api/x'),error=>error.status===502);
});
test('timeouts abort the actual fetch and caller cancellation stays silent', async () => {
  const w=runtime((url,options)=>new Promise((resolve,reject)=>options.signal.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')))));
  await assert.rejects(w.Cms.api.request('/api/x',{timeout:15}),error=>error.status===504);
  const abort=new AbortController(); const pending=w.Cms.api.request('/api/x',{signal:abort.signal}); abort.abort();
  await assert.rejects(pending,error=>error.name==='AbortError');
});
test('pagination handles empty, exact last page and shrunken results without NaN', () => {
  const { state }=runtime(()=>{}).Cms.Pagination;
  assert.equal(state({page:1,size:10,total:0}).pages,1); assert.equal(state({page:1,size:10,total:0}).next,false);
  assert.equal(state({page:2,size:10,total:20}).next,false); assert.equal(state({page:2,size:10,total:20}).previous,true);
  assert.equal(state({page:3,size:10,total:5}).pages,1);
  for(const value of [{page:0,size:10,total:1},{page:1,size:101,total:1},{page:1,size:10,total:-1},{page:1,size:1.5,total:3}]) assert.throws(()=>state(value));
});
test('pagination resets size changes to page one and blocks duplicate requests while busy', async () => {
  class Element {
    constructor(doc){ this.ownerDocument=doc; this.children=[]; this.events={}; this.classList={add(){}}; }
    append(...children){this.children.push(...children);} replaceChildren(){this.children=[];} setAttribute(){} addEventListener(name,fn){this.events[name]=fn;}
  }
  const doc={createElement(){return new Element(doc);}}, host=new Element(doc); let finish, calls=0;
  const w=runtime(()=>{});
  const pager=new w.Cms.Pagination(host, query=>{calls++; assert.equal(query.page,1); assert.equal(query.size,20); return new Promise(resolve=>finish=resolve);});
  pager.render({page:3,size:5,total:13}); const select=host.children[1].children[0]; select.value='20'; select.events.change();
  pager.change(1,20); await Promise.resolve(); assert.equal(calls,1); assert.equal(pager.busy,true);
  finish(); await new Promise(resolve=>setImmediate(resolve)); assert.equal(pager.busy,false);
  let reported; w.Cms.shell={report(error){reported=error.status;}};
  pager.onChange=()=>Promise.reject(new w.Cms.api.ApiError(503)); pager.change(1,20);
  await new Promise(resolve=>setImmediate(resolve)); assert.equal(reported,503); assert.equal(pager.busy,false);
});
