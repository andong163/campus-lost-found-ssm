const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const web=path.resolve(__dirname,'../../main/webapp');
const tick=()=>new Promise(resolve=>setImmediate(resolve));

class Element extends EventTarget {
  constructor(tag) {
    super(); this.tagName=tag; this.children=[]; this.dataset={};
    this.classList={add(){}}; this.textContent='';
  }
  setAttribute() {}
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.children=children; }
  querySelector(tag) { return this.children.find(child=>child.tagName===tag); }
}

async function navigation(user, injected=[]) {
  const host=new Element('div'),main=new Element('main'),body=new Element('body');
  const document={
    body,
    createElement:tag=>new Element(tag),
    querySelector:selector=>selector==='main'?main:host,
    querySelectorAll:()=>[],
    addEventListener(){}
  };
  const window=new EventTarget();
  window.location={pathname:'/cms/pages/index.html'};
  window.Cms={api:{
    root:'https://campus.test/cms/',
    request:async()=>user,
    ApiError:class extends Error {}
  }};
  window.fetch=async url=>{
    const module=new URL(url).pathname.split('/').at(-2);
    const value=JSON.parse(fs.readFileSync(path.join(web,'pages',module,'module.json'),'utf8'));
    if(module==='claims')value.entries.push(...injected);
    return {ok:true,json:async()=>value};
  };
  vm.runInNewContext(fs.readFileSync(path.join(web,'assets/js/shell.js'),'utf8'),{window,document,URL});
  await window.Cms.shell.ready;
  await tick();
  const links=[];
  function visit(node) {
    if(node.tagName==='a')links.push({label:node.textContent,url:node.href});
    for(const child of node.children)visit(child);
  }
  visit(host);
  return links;
}

test('student can reach own claims and handover through the real manifests',async()=>{
  const links=await navigation({id:1,roles:['EDITOR'],permissions:['claim:handover:own']});
  assert.ok(links.some(link=>link.url==='https://campus.test/cms/claim/mine.html'));
  assert.ok(links.some(link=>link.url==='https://campus.test/cms/claim/handover.html'));
  assert.ok(!links.some(link=>link.url.endsWith('/claim/manage.html')||link.url.endsWith('/audit/logs.html')));
});

test('guest navigation hides private claims and audit links',async()=>{
  const links=await navigation(null);
  assert.ok(!links.some(link=>/\/(?:claim|audit)\//.test(link.url)));
});

test('staff navigation exposes only authorized handling and audit routes',async()=>{
  const links=await navigation({id:2,roles:[],permissions:['claim:handle','audit:view']});
  assert.ok(links.some(link=>link.url.endsWith('/claim/manage.html')));
  assert.ok(links.some(link=>link.url.endsWith('/audit/logs.html')));
  assert.ok(!links.some(link=>link.url.endsWith('/claim/handover.html')));
});

test('manifest cannot add foreign, traversal or unregistered module routes',async()=>{
  const invalid=['https://evil.test/claim/mine.html','claim/../audit/logs.html',
    'pages/auth/users.html','claim/unknown.html','//evil.test/claim/mine.html'];
  const links=await navigation({id:3,roles:['ADMIN'],permissions:[]},
    invalid.map((page,index)=>({label:'invalid-'+index,page})));
  assert.ok(!links.some(link=>link.label.startsWith('invalid-')));
  assert.ok(links.some(link=>link.url.endsWith('/audit/logs.html')));
});
