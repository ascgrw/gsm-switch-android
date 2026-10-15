const vm=require('node:vm'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
class Element{
 constructor(){this.value='';this.hidden=false;this.dataset={};this.textContent='';this.innerHTML='';this.handlers={};this.disabled=false;this.open=false;this.options=['30','60','120','300','600','900'].map(value=>({value}));this.classList={toggle(){}};}
 addEventListener(k,f){this.handlers[k]=f;}setAttribute(k,v){this[k]=v;}removeAttribute(k){delete this[k];}append(o){this.options.push(o);}
 showModal(){this.open=true;}close(){this.open=false;this.handlers.close?.();}reset(){for(const id of ['switchName','switchPhone','devicePin','editSwitch'])get(id).value='';get('switchOutput').value='1';get('switchTimer').value='300';}
}
const elements={},get=id=>elements[id]??=new Element(),pins=new Map(),calls=[],timeouts=[];
let sendAllowed=true,records=[],storage='',permissionRequests=0;
const bridge={snapshot:()=>JSON.stringify({sendPermission:sendAllowed,senderOnly:true,simLabel:'Default SIM',records}),hasPin:p=>pins.has(p),savePin:(p,pin)=>{assert.match(pin,/^\d{6,12}$/);pins.set(p,pin);return JSON.stringify({ok:true});},forgetPin:p=>pins.delete(p),requestSms:()=>permissionRequests++,chooseSim(){},exportFile(){},sendCommand:(phone,rid,action)=>{if(!sendAllowed)return JSON.stringify({ok:false,error:'SMS permission denied'});calls.push({phone,rid,action});return JSON.stringify({ok:true,status:'Queued · awaiting SMS result'});}};
const context=vm.createContext({window:{GsmAndroid:bridge},GsmAndroid:bridge,document:{getElementById:get,querySelectorAll:()=>[],createElement:()=>new Element(),addEventListener(){},visibilityState:'visible'},localStorage:{getItem:()=>null,setItem:(k,v)=>storage=v},crypto:require('node:crypto').webcrypto,setTimeout:(f,n)=>{timeouts.push({f,n});return timeouts.length;},clearTimeout(){},setInterval(){},confirm:()=>true,Blob,URL});
vm.runInContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/app.js'),'utf8'),context);
const run=code=>vm.runInContext(code,context),event={preventDefault(){}},flush=()=>{for(const t of timeouts.splice(0))if(t.n===500)t.f();};
function add(name,phone,output,pin=''){run('openEditor()');get('switchName').value=name;get('switchPhone').value=phone;get('switchOutput').value=String(output);get('devicePin').value=pin;get('switchForm').onsubmit(event);}
function markSent(){records=calls.map(c=>({...c,created:Date.now(),status:'SMS sent · check reply in Messages'}));run('syncNative()');}
assert.equal(run('allSwitches().length'),0);assert.ok(get('switchCards').innerHTML.includes('Add your first switch'));
add('Water pump','+92 300 1234567',1,'654321');assert.equal(run('allSwitches().length'),1);assert.equal(run('devices[0].phone'),'+923001234567');assert.ok(!storage.includes('654321'));
add('Outdoor light','+923001234567',2);assert.equal(run('devices.length'),1);assert.equal(run('allSwitches().length'),2);
add('Bedroom','+923001234568',1,'765432');assert.equal(run('devices.length'),2);assert.equal(run('allSwitches().length'),3);
add('Duplicate','+923001234567',1);assert.equal(run('allSwitches().length'),3);assert.ok(get('formError').textContent.includes('already added'));get('switchDialog').close();
const first=run('allSwitches()[0].id');run(`command(${JSON.stringify(first)},'ON')`);assert.equal(calls.at(-1).action,'R1 ON 300');assert.equal(calls.at(-1).phone,'+923001234567');flush();markSent();
run(`command(${JSON.stringify(first)},'OFF')`);assert.equal(calls.at(-1).action,'R1 OFF');flush();markSent();
const light=run('allSwitches()[1].id');run(`command(${JSON.stringify(light)},'ON')`);assert.equal(calls.at(-1).action,'R2 ON');flush();markSent();
let start=calls.length;run("batch('ON')");assert.deepEqual(calls.slice(start).map(c=>c.action),['ALL ON 300','R1 ON 300']);flush();markSent();
start=calls.length;run("batch('OFF')");assert.deepEqual(calls.slice(start).map(c=>c.action),['ALL OFF','R1 OFF']);flush();markSent();
assert.ok(get('activityList').innerHTML.includes('Sent'));assert.ok(!get('switchCards').innerHTML.includes('Reported ON'));assert.ok(!get('switchCards').innerHTML.includes('ON · sent'));
sendAllowed=false;run('syncNative()');start=calls.length;run(`command(${JSON.stringify(first)},'ON')`);assert.equal(calls.length,start);assert.equal(permissionRequests,1);flush();sendAllowed=true;run('syncNative()');
const bedroom=run('allSwitches()[2].id');run(`openEditor(${JSON.stringify(bedroom)})`);get('switchOutput').value='2';get('switchForm').onsubmit(event);assert.equal(run('devices.length'),2);assert.equal(run('allSwitches().length'),3);const moved=run('allSwitches()[2].id');run(`command(${JSON.stringify(moved)},'OFF')`);assert.equal(calls.at(-1).action,'R2 OFF');assert.equal(calls.at(-1).phone,'+923001234568');flush();
run(`openEditor(${JSON.stringify(first)})`);get('removeSwitch').onclick();assert.equal(run('allSwitches().length'),2);assert.equal(pins.has('+923001234567'),true);
run(`openEditor(${JSON.stringify(light)})`);get('removeSwitch').onclick();assert.equal(run('allSwitches().length'),1);assert.equal(pins.has('+923001234567'),false);
const legacy={devices:[{id:'legacy',name:'Farm',phone:'+923001234570',channels:['Pump','Light'],seconds:300}],history:[]};context.legacy=legacy;assert.equal(run('validBackup(legacy)'),true);
context.invalid={...legacy,devices:[{...legacy.devices[0],enabledChannels:[1,1]}]};assert.equal(run('validBackup(invalid)'),false);
assert.equal(run('validBackup(JSON.parse(localStorageData))'.replace('localStorageData',JSON.stringify(storage))),true);
assert.ok(!storage.includes('654321'));assert.ok(!storage.includes('765432'));
console.log('PASS: add/edit/remove switches, shared controller and PIN, duplicate prevention, exact R1/R2 and All commands, permission denial, no false relay state, output reassignment, legacy backups and PIN exclusion.');
