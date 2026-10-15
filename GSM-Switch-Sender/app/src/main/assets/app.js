'use strict';
const NATIVE=typeof window.GsmAndroid?.sendCommand==='function';
const $=id=>document.getElementById(id),KEY='gsm-switch-v1';
const escapeHtml=x=>String(x).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const validPhone=x=>/^\+[1-9]\d{7,14}$/.test(x),normalizePhone=x=>x.replace(/[\s()-]/g,'');
const validPin=x=>/^\d{6,12}$/.test(x);
const requestId=()=>Array.from(crypto.getRandomValues(new Uint8Array(4)),n=>n.toString(16).padStart(2,'0')).join('').toUpperCase();
const icon=paths=>`<svg viewBox="0 0 24 24" aria-hidden="true">${paths}</svg>`;
const icons={power:icon('<path d="M12 3v9M6.2 5.8a9 9 0 1 0 11.6 0"/>'),off:icon('<path d="M12 3v5M5.5 5.5a9 9 0 0 0 13 13M18.4 5.8a9 9 0 0 1 2.4 9M3 3l18 18"/>'),plus:icon('<path d="M12 5v14M5 12h14"/>'),switch:icon('<rect x="3" y="7" width="18" height="10" rx="5"/><circle cx="15" cy="12" r="2"/>'),clock:icon('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'),settings:icon('<path d="M4 7h16M4 17h16"/><circle cx="8" cy="7" r="3" fill="#111b17"/><circle cx="16" cy="17" r="3" fill="#111b17"/>'),edit:icon('<path d="m15 4 5 5M4 20l4-1 12-12a2.8 2.8 0 0 0-4-4L4 15v5Z"/>')};
let devices=[],history=[],snapshot={sendPermission:false,records:[]},staged=null,busy=false;
function enabled(d){return d.enabledChannels||[1,2];}
function validBackup(s){return s&&Array.isArray(s.devices)&&s.devices.length<=30&&s.devices.every(d=>typeof d.id==='string'&&typeof d.name==='string'&&d.name.length<=50&&validPhone(d.phone)&&Array.isArray(d.channels)&&d.channels.length===2&&d.channels.every(c=>typeof c==='string'&&c.length>0&&c.length<=35)&&Number.isInteger(d.seconds)&&d.seconds>=1&&d.seconds<=900&&(!d.enabledChannels||Array.isArray(d.enabledChannels)&&d.enabledChannels.length>0&&d.enabledChannels.length<=2&&new Set(d.enabledChannels).size===d.enabledChannels.length&&d.enabledChannels.every(n=>n===1||n===2)))&&new Set(s.devices.map(d=>d.id)).size===s.devices.length&&new Set(s.devices.map(d=>d.phone)).size===s.devices.length&&Array.isArray(s.history)&&s.history.length<=300&&s.history.every(h=>typeof h.time==='string'&&Number.isFinite(Date.parse(h.time))&&typeof h.device==='string'&&typeof h.action==='string'&&typeof h.status==='string'&&/^[A-F0-9]{8}$/.test(h.rid));}
try{const s=JSON.parse(localStorage.getItem(KEY)||'null');if(validBackup(s)){devices=s.devices.filter(d=>!(d.id==='demo1'&&!d.configured));history=s.history;}}catch{}
function save(){try{localStorage.setItem(KEY,JSON.stringify({devices,history}));}catch{toast('Could not save. Create a backup in Settings.');}}
function toast(message){$('toast').textContent=message;$('toast').hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('toast').hidden=true,3500);}
function duration(n){return n%60===0?`${n/60} min`:`${n} sec`;}
function clock(t){return new Date(t).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'});}
function statusOf(s){return s.startsWith('SMS sent')?{label:'Sent',css:'sent'}:s.startsWith('SMS failed')||s.startsWith('Unconfirmed')?{label:'Failed',css:'failed'}:s.startsWith('Queued')?{label:'Sending',css:''}:{label:s.startsWith('Demo')?'Demo':'Unconfirmed',css:''};}
function actionOf(h,d){const m=/^R([12]) (ON|OFF)/.exec(h.action);if(m)return {name:h.switchName||d?.channels[Number(m[1])-1]||h.name||'Switch',action:m[2],output:Number(m[1])};return{name:h.name||d?.name||'Controller',action:h.action==='STATUS'?'Status':h.action.startsWith('ALL ON')?'All ON':'All OFF',output:0};}
function allSwitches(){return devices.flatMap(d=>enabled(d).map(output=>({d,output,id:d.id+':'+output,name:d.channels[output-1]})));}
function render(){
  const switches=allSwitches();$('switchCount').textContent=switches.length?`${switches.length} switch${switches.length===1?'':'es'}`:'';
  $('readyBadge').textContent=snapshot.sendPermission?'SMS enabled':'Set up SMS';$('readyBadge').classList.toggle('waiting',!snapshot.sendPermission);
  $('permissionBanner').hidden=!NATIVE||snapshot.sendPermission;$('bulkBar').hidden=!switches.length;$('allOn').hidden=switches.length<2;$('allOff').hidden=switches.length<2;
  $('switchCards').innerHTML=switches.length?switches.map(s=>{
    const recent=history.find(h=>h.device===s.d.id&&(h.action.startsWith('R'+s.output+' ')||h.action.startsWith('ALL ')));
    const result=recent?statusOf(recent.status):null,command=recent?actionOf(recent,s.d).action.replace('All ',''):'';
    return `<article class="switch-card"><div class="card-top"><span class="switch-icon">${icons.switch}</span><div class="switch-title"><h3>${escapeHtml(s.name)}</h3><p class="phone">${escapeHtml(s.d.phone)}</p></div><button class="icon-button" data-edit="${escapeHtml(s.id)}" aria-label="Edit ${escapeHtml(s.name)}">${icons.edit}</button></div><div class="card-meta">${s.output===1?icons.clock:icons.power}<span>${s.output===1?'Auto off · '+duration(s.d.seconds):'Manual off'}</span></div><div class="switch-controls"><button class="primary" data-switch="${escapeHtml(s.id)}" data-action="ON" aria-label="Turn ${escapeHtml(s.name)} on">${icons.power}ON</button><button class="off-button" data-switch="${escapeHtml(s.id)}" data-action="OFF" aria-label="Turn ${escapeHtml(s.name)} off">${icons.off}OFF</button></div><div class="last-command"><span class="${result?.css||''}">${recent?escapeHtml(command)+' · '+result.label.toLowerCase():'No commands yet'}</span><span>${recent?clock(recent.time):''}</span></div></article>`;
  }).join(''):`<div class="empty"><div class="brand-icon">${icons.switch}</div><h2>Add your first switch</h2><p>Keep your devices one tap away.</p><button class="primary" data-add>Add switch</button></div>`;
  $('activityList').innerHTML=history.length?history.map(h=>{const d=devices.find(d=>d.id===h.device),a=actionOf(h,d),s=statusOf(h.status);return `<article class="activity-item"><span class="activity-icon ${a.action.includes('OFF')?'off':''}">${escapeHtml(a.action==='Status'?'?':a.action.includes('OFF')?'OFF':'ON')}</span><div class="activity-info"><b>${escapeHtml(a.name)}</b><p>${escapeHtml(a.action)}${a.action.includes('ON')&&d&&h.action.endsWith(String(d.seconds))?' · '+duration(d.seconds):''}</p></div><div class="activity-result ${s.css}">${s.label}<small>${clock(h.time)}</small></div></article>`;}).join(''):'<div class="plain-empty">Your commands will appear here.</div>';
  $('permissionStatus').textContent=snapshot.sendPermission?'Enabled':'Not enabled';$('allowSms').textContent=snapshot.sendPermission?'Enabled':'Enable';$('allowSms').disabled=snapshot.sendPermission;$('allowSms').hidden=snapshot.sendPermission;$('permissionStatus').classList.toggle('enabled',snapshot.sendPermission);
  $('simStatus').textContent=snapshot.simLabel||'Default SIM';document.querySelectorAll('[data-switch]').forEach(b=>b.disabled=busy);for(const id of ['allOn','allOff','requestStatus'])$(id).disabled=busy;
}
function navigate(page){if(!['switches','history','settings'].includes(page))return;document.querySelectorAll('.page').forEach(p=>p.hidden=p.id!==page);document.querySelectorAll('[data-page]').forEach(b=>b.setAttribute('aria-selected',String(b.dataset.page===page)));}
function pinSaved(phone){try{return NATIVE&&GsmAndroid.hasPin(phone);}catch{return false;}}
function updateForm(){const phone=normalizePhone($('switchPhone').value.trim()),saved=pinSaved(phone);$('pinHint').hidden=!saved;$('devicePin').required=NATIVE&&!saved;$('timerField').hidden=$('switchOutput').value!=='1';}
function openEditor(id){
  const s=allSwitches().find(s=>s.id===id);$('switchForm').reset();$('editSwitch').value=id||'';$('switchDialogTitle').textContent=s?'Edit switch':'Add switch';$('removeSwitch').hidden=!s;$('formError').hidden=true;
  if(s){$('switchName').value=s.name;$('switchPhone').value=s.d.phone;$('switchOutput').value=String(s.output);if(!Array.from($('switchTimer').options).some(o=>o.value===String(s.d.seconds))){const o=document.createElement('option');o.value=String(s.d.seconds);o.textContent=duration(s.d.seconds);$('switchTimer').append(o);}$('switchTimer').value=String(s.d.seconds);}
  updateForm();$('switchDialog').showModal();
}
function formError(message){$('formError').textContent=message;$('formError').hidden=false;}
$('switchForm').onsubmit=e=>{
  e.preventDefault();const old=allSwitches().find(s=>s.id===$('editSwitch').value),name=$('switchName').value.trim(),phone=normalizePhone($('switchPhone').value.trim()),output=Number($('switchOutput').value),pin=$('devicePin').value;
  if(!name||name.length>35)return formError('Enter a switch name.');if(!validPhone(phone))return formError('Use a number with country code, such as +923001234567.');
  let target=devices.find(d=>d.phone===phone);
  if(target&&enabled(target).includes(output)&&!(old&&old.d.id===target.id&&old.output===output))return formError('That output is already added. Choose the other output or another number.');
  if(!target&&devices.length>=30&&!(old&&enabled(old.d).length===1))return formError('Maximum 30 controllers.');
  if(pin&&!validPin(pin))return formError('Use a PIN of 6–12 digits.');if(NATIVE&&!pin&&!pinSaved(phone))return formError('Enter the device PIN.');
  if(pin&&NATIVE){try{const r=JSON.parse(GsmAndroid.savePin(phone,pin));if(!r.ok)return formError(r.error);}catch{return formError('Could not save PIN.');}}
  if(old&&(old.d.phone!==phone||old.output!==output)){old.d.enabledChannels=enabled(old.d).filter(n=>n!==old.output);if(!old.d.enabledChannels.length&&old.d.phone!==phone){devices=devices.filter(d=>d.id!==old.d.id);if(NATIVE)GsmAndroid.forgetPin(old.d.phone);}}
  if(!target){target={id:requestId(),configured:true,name:name+' controller',phone,channels:['Switch 1','Switch 2'],seconds:300,enabledChannels:[]};devices.push(target);}
  target.channels[output-1]=name;target.enabledChannels=Array.from(new Set([...enabled(target),output]));target.configured=true;if(output===1)target.seconds=Number($('switchTimer').value);
  save();render();$('switchDialog').close();toast(old?'Switch updated':'Switch added');
};
$('removeSwitch').onclick=()=>{const old=allSwitches().find(s=>s.id===$('editSwitch').value);if(!old||!confirm('Remove '+old.name+'?'))return;old.d.enabledChannels=enabled(old.d).filter(n=>n!==old.output);if(!old.d.enabledChannels.length){devices=devices.filter(d=>d.id!==old.d.id);if(NATIVE)GsmAndroid.forgetPin(old.d.phone);}save();render();$('switchDialog').close();toast('Switch removed');};
function addLog(d,action,rid,status,switchName){history.unshift({device:d.id,phone:d.phone,name:d.name,switchName,action,rid,status,time:new Date().toISOString(),source:'auto'});history=history.slice(0,300);save();render();}
function send(d,action,switchName){
  if(!NATIVE){toast('Sending is available in the Android app.');return false;}
  if(!snapshot.sendPermission){requestSms();toast('Allow SMS, then tap the switch again.');return false;}
  const rid=requestId();if(!pinSaved(d.phone)){staged={d,action,rid,switchName};$('pinTarget').textContent=switchName||d.name;$('commandPin').value='';$('pinDialog').showModal();return false;}
  return submit(d,action,rid,switchName);
}
function submit(d,action,rid,switchName){try{const result=JSON.parse(GsmAndroid.sendCommand(d.phone,rid,action));if(!result.ok){toast(result.error);return false;}addLog(d,action,rid,result.status,switchName);syncNative();return true;}catch{toast('Could not send. Try again.');return false;}}
function command(id,action){const s=allSwitches().find(s=>s.id===id);if(!s||busy||!['ON','OFF'].includes(action))return;busy=true;render();try{send(s.d,`R${s.output} ${action}${s.output===1&&action==='ON'?' '+s.d.seconds:''}`,s.name);}finally{setTimeout(()=>{busy=false;render();},500);}}
function batch(action){if(busy)return;const targets=devices.filter(d=>enabled(d).length);if(!targets.length)return;
  if(!NATIVE){toast('Sending is available in the Android app.');return;}if(!snapshot.sendPermission){requestSms();return;}
  if(targets.some(d=>!pinSaved(d.phone))){toast('Set each device PIN before using All controls.');return;}
  busy=true;render();let count=0;
  try{for(const d of targets){if(action==='STATUS'){count+=Number(send(d,'STATUS',d.name));continue;}if(enabled(d).length===2){count+=Number(send(d,`ALL ${action}${action==='ON'?' '+d.seconds:''}`,d.name));}else{const output=enabled(d)[0];count+=Number(send(d,`R${output} ${action}${output===1&&action==='ON'?' '+d.seconds:''}`,d.channels[output-1]));}}}finally{setTimeout(()=>{busy=false;render();},500);}if(count)toast(`${count} message${count===1?'':'s'} queued`);
}
$('pinForm').onsubmit=e=>{e.preventDefault();if(!staged)return;const pin=$('commandPin').value;if(!validPin(pin)){toast('Use a PIN of 6–12 digits.');return;}try{const r=JSON.parse(GsmAndroid.savePin(staged.d.phone,pin));if(!r.ok){toast(r.error);return;}const pending=staged;$('pinDialog').close();submit(pending.d,pending.action,pending.rid,pending.switchName);}catch{toast('Could not save PIN.');}};
for(const id of ['switchDialog','pinDialog'])$(id).addEventListener('close',()=>{$('devicePin').value='';$('commandPin').value='';if(id==='pinDialog')staged=null;});
document.addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;if(b.dataset.page)navigate(b.dataset.page);if(b.dataset.close)$(b.dataset.close).close();if(b.dataset.edit)openEditor(b.dataset.edit);if(b.dataset.add!==undefined)openEditor();if(b.dataset.switch)command(b.dataset.switch,b.dataset.action);});
$('addSwitch').onclick=()=>openEditor();$('switchOutput').onchange=updateForm;$('switchPhone').oninput=updateForm;
$('allOn').onclick=()=>batch('ON');$('allOff').onclick=()=>batch('OFF');$('requestStatus').onclick=()=>batch('STATUS');
function requestSms(){if(NATIVE)GsmAndroid.requestSms();else toast('SMS setup is available in the Android app.');}
$('enableSms').onclick=requestSms;$('allowSms').onclick=requestSms;$('chooseSim').onclick=()=>{if(NATIVE)GsmAndroid.chooseSim();else toast('SIM selection is available in the Android app.');};
function download(name,data){if(NATIVE){GsmAndroid.exportFile(name,data);return;}const url=URL.createObjectURL(new Blob([data],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
$('exportBackup').onclick=()=>download('GSM-Switch-Backup.json',JSON.stringify({devices,history},null,2));$('exportHistory').onclick=()=>download('GSM-Switch-History.json',JSON.stringify(history,null,2));
$('importBackup').onchange=async e=>{const f=e.target.files[0];if(!f)return;try{if(f.size>1000000)throw Error('Backup is too large.');const s=JSON.parse(await f.text());if(!validBackup(s))throw Error('Invalid backup.');if(confirm('Replace switches and activity with this backup?')){devices=s.devices.filter(d=>!(d.id==='demo1'&&!d.configured));history=s.history;save();syncNative();render();toast('Backup restored');}}catch(e){toast(e.message);}e.target.value='';};
function syncNative(){if(!NATIVE){render();return;}try{const next=JSON.parse(GsmAndroid.snapshot()),snapshotChanged=JSON.stringify(next)!==JSON.stringify(snapshot);snapshot=next;const records=Array.isArray(snapshot.records)?snapshot.records:[];let changed=false;for(const r of records){if(!/^[A-F0-9]{8}$/.test(r.rid))continue;const d=devices.find(d=>d.phone===r.phone);let h=history.find(h=>h.rid===r.rid&&(h.phone===r.phone||h.device===d?.id));if(!h&&!d)continue;if(!h){h={device:d.id,phone:d.phone,name:d.name,action:r.action,rid:r.rid,status:r.status,time:new Date(r.created).toISOString(),source:'auto'};history.unshift(h);changed=true;}if(h.status!==r.status){h.status=r.status;changed=true;}}history.sort((a,b)=>Date.parse(b.time)-Date.parse(a.time));history=history.slice(0,300);if(changed)save();if(changed||snapshotChanged)render();}catch{toast('Could not refresh SMS status.');}}
for(const [id,name]of [['brandIcon','power'],['addIcon','plus'],['controlsIcon','switch'],['activityIcon','clock'],['settingsIcon','settings']])$(id).innerHTML=icons[name];
setInterval(()=>{if(document.visibilityState==='visible')syncNative();},2000);document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')syncNative();});syncNative();
