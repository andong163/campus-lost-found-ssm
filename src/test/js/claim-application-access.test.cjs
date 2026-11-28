const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const tick=()=>new Promise(resolve=>setImmediate(resolve));

function page(user) {
  class Element {
    constructor(){this.hidden=false;this.disabled=false;this.value='';this.children=[];this.events={};}
    addEventListener(type,handler){this.events[type]=handler;}
    replaceChildren(...children){this.children=children;}
    append(...children){this.children.push(...children);}
    appendChild(child){this.children.push(child);return child;}
    reset(){} focus(){}
    querySelector(){return element('submit');}
  }
  const elements=new Map(),events={},posts=[];
  const element=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id);};
  element('claim-application').hidden=true;
  let accountHandler;
  const Cms={shell:{user,hasPermission:code=>!!Cms.shell.user&&(Cms.shell.user.roles?.includes('ADMIN')||Cms.shell.user.permissions?.includes(code))},Pagination:class{render(){}}};
  const YC={
    request:async()=>({records:[],total:0}),
    post:async(...args)=>posts.push(args),
    text:(tag,text)=>Object.assign(new Element(),{textContent:text}),
    row:()=>new Element(),setMessage(){},errorMessage:error=>error.message,
    onAccountChange:handler=>{accountHandler=handler;}
  };
  const source=fs.readFileSync(path.resolve(__dirname,'../../main/webapp/claim/mine.html'),'utf8');
  const inline=[...source.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)].at(-1)[1];
  vm.runInNewContext(inline,{Cms,YC,URLSearchParams,window:{addEventListener:(type,handler)=>events[type]=handler},
    document:{getElementById:element,createElement:()=>new Element(),createTextNode:text=>({textContent:text})},location:{search:''}});
  function authenticate(next){Cms.shell.user=next;accountHandler(next);events['cms:authchange']({detail:{user:next}});}
  authenticate(user);
  return {element,posts,authenticate,submit:()=>element('applyForm').events.submit({preventDefault(){},currentTarget:element('applyForm')})};
}

for(const user of [
  {id:1,roles:['ADMIN'],permissions:[]},
  {id:2,roles:['EDITOR'],permissions:['claim:create']}
])test(`${user.roles[0]} can use the claim application allowed by server permissions`,async()=>{
  const p=page(user);await tick();
  assert.equal(p.element('claim-application').hidden,false);
  p.element('itemId').value='42';p.element('claimDescription').value='本人可以提供物品独有特征进行核实';p.element('contactInfo').value='校园联系';
  await p.submit();
  assert.equal(p.posts.length,1);assert.equal(p.posts[0][0],'/api/items/42/claims');
  assert.equal(p.posts[0][1].claimDescription,'本人可以提供物品独有特征进行核实');
});

for(const user of [null,{id:3,roles:['VIEWER'],permissions:[]}])test(`${user?'read-only user':'guest'} cannot submit a claim without permission`,async()=>{
  const p=page(user);await tick();
  assert.equal(p.element('claim-application').hidden,true);await p.submit();assert.equal(p.posts.length,0);
});
