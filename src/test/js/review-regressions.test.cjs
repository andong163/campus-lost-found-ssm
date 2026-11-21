const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const web=path.resolve(__dirname,'../../main/webapp');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
class Element {
  constructor(){this.children=[];this.events={};this.value='';this.hidden=false;this._text='';}
  set textContent(value){this._text=value;this.children=[];}
  get textContent(){return this._text+this.children.map(child=>child.textContent).join('');}
  append(...children){this.children.push(...children);}
  replaceChildren(...children){this._text='';this.children=children;}
  addEventListener(name,action){this.events[name]=action;}
  setAttribute(){} removeAttribute(){} focus(){}
}
function dom(){const nodes=new Map();const el=id=>{if(!nodes.has(id))nodes.set(id,new Element());return nodes.get(id);};
  return {el,document:{getElementById:el,querySelector:selector=>el(selector.slice(1)),
    createElement:()=>new Element(),createTextNode:text=>({textContent:text})}};}

for(const lateError of [false,true])test(`public search ignores an older ${lateError?'failed':'successful'} response`,async()=>{
  const {el,document}=dom(),callbacks={},pending=[];
  document.addEventListener=(event,fn)=>callbacks[event]=fn;
  const form=el('filter');form.elements=Object.fromEntries(
    ['keyword','location','categoryId','from','to','itemType','resolutionStatus','sortBy','direction'].map(name=>[name,new Element()]));
  form.elements.sortBy.value='publishedAt';form.elements.direction.value='desc';
  class FormData {constructor(form){this.form=form;} *[Symbol.iterator](){for(const [k,v] of Object.entries(this.form.elements))yield [k,v.value];}}
  const Cms={shell:{ready:Promise.resolve()},api:{request:url=>url==='/api/public/categories'?Promise.resolve([]):
    new Promise((resolve,reject)=>pending.push({url,resolve,reject}))}};
  const inline=[...fs.readFileSync(path.join(web,'pages/items/public.html'),'utf8').matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)].at(-1)[1];
  vm.runInNewContext(inline,{document,Cms,FormData,URLSearchParams,history:{replaceState(){}},location:{search:''},
    PublicDateFilters:require(path.join(web,'assets/js/public-date-filters.js'))});
  await callbacks.DOMContentLoaded();await tick();
  pending.shift().resolve({total:0,records:[]});await tick();
  form.elements.keyword.value='甲组';form.onsubmit({preventDefault(){}});
  form.elements.keyword.value='乙组';form.onsubmit({preventDefault(){}});
  const [oldest,latest]=pending;
  latest.resolve({total:1,records:[{id:2,title:'乙组物品',itemType:'LOST',location:'图书馆',resolutionStatus:'OPEN'}]});await tick();
  if(lateError)oldest.reject(new Error('旧请求失败'));
  else oldest.resolve({total:1,records:[{id:1,title:'甲组物品',itemType:'FOUND',location:'教室',resolutionStatus:'OPEN'}]});
  await tick();
  assert.match(el('list').textContent,/乙组物品/);assert.doesNotMatch(el('list').textContent,/甲组物品/);
  assert.equal(el('notice').textContent,'');
});

test('expired session closes and clears the role assignment modal',async()=>{
  const {el,document}=dom(),events={};
  Object.assign(el('filter-form'),Object.fromEntries(['username','displayName','roleCode','enabled'].map(name=>[name,{value:''}])));
  const Cms={shell:{ready:Promise.resolve(),user:{id:1},hasPermission:()=>true},
    Pagination:class {constructor(){this.value={page:1,size:10};}render(){}},
    api:{request:async url=>url==='/api/roles'?[{code:'EDITOR',name:'学生'}]:
      {records:[{id:2,username:'student02',displayName:'学生',enabled:1,roles:['EDITOR']}],page:1,size:10,total:1}}};
  vm.runInNewContext(fs.readFileSync(path.join(web,'assets/js/auth/users.js'),'utf8'),{
    document,Cms,URLSearchParams,window:{addEventListener:(event,fn)=>events[event]=fn,confirm:()=>true}});
  await tick();
  function find(node,label){if(node.textContent===label&&node.events?.click)return node;
    for(const child of node.children||[]){const result=find(child,label);if(result)return result;}}
  find(el('rows'),'分配角色').events.click();
  assert.equal(el('role-modal').hidden,false);
  Cms.shell.user=null;events['cms:authchange']({detail:{user:null}});
  assert.equal(el('role-modal').hidden,true);assert.equal(el('role-modal-target').textContent,'');
  assert.equal(el('role-options').children.length,0);assert.equal(el('workspace').hidden,true);
});

test('create page clears the previous account before a slow category response can enable saving',async()=>{
  const {el,document}=dom(),events={};let categories,reset=0,reload=0;
  const form=el('form'),button=new Element();form.querySelector=()=>button;form.reset=()=>reset++;
  const Cms={shell:{ready:Promise.resolve(),user:{id:1},hasPermission:()=>true},
    api:{request:()=>new Promise(resolve=>categories=resolve)}};
  vm.runInNewContext(fs.readFileSync(path.join(web,'assets/js/item-create.js'),'utf8'),{
    document,Cms,window:{addEventListener:(type,fn)=>events[type]=fn},location:{reload(){reload++;}}});
  await tick();Cms.shell.user={id:2};events['cms:authchange']({detail:{user:Cms.shell.user}});
  categories([]);await tick();
  assert.equal(reset,1);assert.equal(reload,1);assert.equal(form.hidden,true);assert.equal(button.disabled,true);
  assert.equal(form.events.submit,undefined);
});

for(const module of ['users','roles'])test(`${module} management reloads on re-login without duplicating handlers`,async()=>{
  const {el,document}=dom(),events={};let reload=0;
  Object.assign(el('filter-form'),Object.fromEntries(['username','displayName','roleCode','enabled'].map(name=>[name,{value:''}])));
  const Cms={shell:{ready:Promise.resolve(),user:{id:1},hasPermission:()=>true},
    Pagination:class{constructor(){this.value={page:1,size:10};}render(){}},
    api:{request:async url=>url==='/api/roles'||url==='/api/permissions'?[]:{records:[],total:0,page:1,size:10}}};
  vm.runInNewContext(fs.readFileSync(path.join(web,`assets/js/auth/${module}.js`),'utf8'),{
    document,Cms,URLSearchParams,window:{addEventListener:(type,fn)=>events[type]=fn,location:{reload(){reload++;}}}});
  await tick();events['cms:authchange']({detail:{user:{id:1}}});assert.equal(reload,0);
  Cms.shell.user=null;events['cms:authchange']({detail:{user:null}});assert.equal(el('workspace').hidden,true);
  Cms.shell.user={id:1};events['cms:authchange']({detail:{user:Cms.shell.user}});assert.equal(reload,1);
});

for(const lateError of [false,true])test(`users filter discards an older ${lateError?'failed':'successful'} response`,async()=>{
  const {el,document}=dom(),pending=[];
  Object.assign(el('filter-form'),Object.fromEntries(['username','displayName','roleCode','enabled'].map(name=>[name,{value:''}])));
  const Cms={shell:{ready:Promise.resolve(),user:{id:1},hasPermission:()=>true},
    Pagination:class{constructor(){this.value={page:1,size:10};}render(){}},
    api:{request:url=>url==='/api/roles'?Promise.resolve([]):new Promise((resolve,reject)=>pending.push({resolve,reject}))}};
  vm.runInNewContext(fs.readFileSync(path.join(web,'assets/js/auth/users.js'),'utf8'),{
    document,Cms,URLSearchParams,window:{addEventListener(){}}});
  const result=username=>({records:[{id:2,username,enabled:true,roles:[]}],total:1,page:1,size:10});
  await tick();pending.shift().resolve(result('initial'));await tick();
  const form=el('filter-form');form.username.value='A';form.events.submit({preventDefault(){}});
  form.username.value='B';form.events.submit({preventDefault(){}});
  pending[1].resolve(result('latest-B'));await tick();
  if(lateError)pending[0].reject(new Error('old-error'));else pending[0].resolve(result('old-A'));
  await tick();assert.match(el('rows').textContent,/latest-B/);assert.doesNotMatch(el('rows').textContent,/old-A/);
  assert.equal(el('alert').textContent,'');
});

test('legacy module writes reuse the shared request with an object body',async()=>{
  const {document}=dom(),calls=[],window={addEventListener(){}};
  vm.runInNewContext(fs.readFileSync(path.join(web,'yangcheng/common.js'),'utf8'),{
    window,document,Cms:{api:{request:async(...args)=>{calls.push(args);return null;}}}});
  await window.YC.post('/api/claims/2/cancel',{version:1,reason:'test'});
  assert.equal(calls[0][1].body.version,1);assert.equal(typeof calls[0][1].body,'object');
});
