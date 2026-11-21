package com.iitsm.web;

/** The single-page browser UI, served by WebServer. It only talks to the REST API, never to the domain classes. */
final class WebPage {
    private WebPage() { }

    static final String HTML = """
<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>I-ITSM</title>
<style>
:root{--bg:#f4f6fa;--card:#fff;--tx:#1c2430;--mu:#667085;--bd:#e2e6ee;--ac:#2f5fd0;--p1:#d92d20;--p2:#f79009;--p3:#2f5fd0;--p4:#667085;--ok:#12803c}
@media(prefers-color-scheme:dark){:root{--bg:#10151d;--card:#18202b;--tx:#e8ecf3;--mu:#98a2b3;--bd:#2a3442;--ac:#6b93f0;--p3:#6b93f0}}
*{box-sizing:border-box}body{margin:0;font:14px system-ui,Segoe UI,sans-serif;background:var(--bg);color:var(--tx)}
header{display:flex;align-items:center;gap:16px;padding:10px 20px;background:var(--card);border-bottom:1px solid var(--bd);position:sticky;top:0;z-index:5}
header b{font-size:16px}header .sp{flex:1}nav{display:flex;gap:4px;flex-wrap:wrap}
nav button{background:none;border:0;padding:8px 12px;border-radius:6px;color:var(--mu);cursor:pointer;font:inherit}
nav button.on{background:var(--ac);color:#fff}main{max-width:1100px;margin:20px auto;padding:0 16px}
.card{background:var(--card);border:1px solid var(--bd);border-radius:10px;padding:16px;margin-bottom:16px}
h2{margin:0 0 12px;font-size:17px}h3{margin:14px 0 6px;font-size:14px}
input,select,textarea,button.b{font:inherit;padding:8px 10px;border:1px solid var(--bd);border-radius:6px;background:var(--bg);color:var(--tx)}
textarea{width:100%;min-height:70px}label{display:block;font-size:12px;color:var(--mu);margin:8px 0 3px}
button.b{background:var(--ac);color:#fff;border:0;cursor:pointer}button.b.g{background:var(--bg);color:var(--tx);border:1px solid var(--bd)}button.b.r{background:var(--p1)}
.row{display:flex;gap:10px;flex-wrap:wrap;align-items:flex-end}.row>*{flex:1;min-width:130px}.row>button{flex:0}
table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:8px;border-bottom:1px solid var(--bd);vertical-align:top}th{font-size:12px;color:var(--mu)}
tr.click{cursor:pointer}tr.click:hover{background:var(--bg)}
.tag{display:inline-block;padding:2px 8px;border-radius:10px;font-size:11px;font-weight:600;color:#fff;background:var(--p4)}
.P1{background:var(--p1)}.P2{background:var(--p2)}.P3{background:var(--p3)}.P4{background:var(--p4)}.st{background:var(--bd);color:var(--tx)}
.BREACHED{background:var(--p1)}.AT_RISK{background:var(--p2)}.ON_TRACK{background:var(--ok)}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px}.stat{text-align:center}.stat b{display:block;font-size:28px}.stat span{color:var(--mu);font-size:12px}
.bar{display:flex;align-items:center;gap:8px;margin:5px 0}.bar i{display:block;height:14px;background:var(--ac);border-radius:4px;min-width:3px}.bar span{width:130px;color:var(--mu)}
#toast{position:fixed;bottom:20px;right:20px;padding:12px 16px;border-radius:8px;color:#fff;display:none;max-width:380px;z-index:20}
#modal{position:fixed;inset:0;background:#0008;display:none;align-items:flex-start;justify-content:center;overflow:auto;padding:30px 10px;z-index:10}
#modal .card{width:min(760px,100%);margin:0}.mu{color:var(--mu)}.note{border-left:3px solid var(--bd);padding:2px 10px;margin:6px 0}
.login{max-width:380px;margin:70px auto}.login input{width:100%}.hint{font-size:12px;color:var(--mu);margin-top:10px;line-height:1.7}
a{color:var(--ac);cursor:pointer}
</style></head><body>
<div id="app"></div><div id="modal" onclick="if(event.target==this)closeM()"><div class="card" id="mbody"></div></div><div id="toast"></div>
<script>
var S={user:null,token:null,tab:null},app=document.getElementById('app');
function esc(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;')}
function toast(m,bad){var t=document.getElementById('toast');t.textContent=m;t.style.background=bad?'#d92d20':'#12803c';t.style.display='block';clearTimeout(t.h);t.h=setTimeout(function(){t.style.display='none'},4000)}
async function api(path,p){
  var r=await fetch('/api/'+path,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-Token':S.token||''},body:new URLSearchParams(p||{}).toString()});
  var d=await r.json();if(!r.ok){toast(d.error||'Error',true);throw new Error(d.error)}return d}
function val(id){var e=document.getElementById(id);return e?e.value:''}
function tag(c,t){return '<span class="tag '+esc(c)+'">'+esc(t)+'</span>'}
function save(){sessionStorage.setItem('iitsm',JSON.stringify({u:S.user,t:S.token,tab:S.tab}))}

/* ---------- login ---------- */
function loginView(reg){
  app.innerHTML='<div class="card login"><h2>I-ITSM '+(reg?'Register':'Sign in')+'</h2>'+
  (reg?'<label>Name</label><input id="n">':'')+'<label>Email</label><input id="e"><label>Password</label><input id="pw" type="password">'+
  '<div class="row" style="margin-top:14px"><button class="b" onclick="'+(reg?'doReg()':'doLogin()')+'">'+(reg?'Create account':'Login')+'</button>'+
  '<button class="b g" onclick="loginView('+!reg+')">'+(reg?'Back':'Register')+'</button></div>'+
  '<div class="hint">Demo logins:<br>Employee eva@corp.com / EmpPass123<br>Technician tara@corp.com / TechPass123<br>Admin admin@corp.com / AdminPass1</div></div>'}
async function doLogin(){try{var d=await api('login',{email:val('e'),password:val('pw')});S.token=d.token;S.user=d;S.tab=null;save();home()}catch(e){}}
async function doReg(){try{await api('register',{name:val('n'),email:val('e'),password:val('pw')});toast('Account created, please log in');loginView(false)}catch(e){}}
async function logout(){try{await api('logout')}catch(e){}S={user:null,token:null,tab:null};sessionStorage.clear();loginView()}

/* ---------- shell ---------- */
var TABS={EMPLOYEE:[['mine','My incidents'],['report','Report incident'],['notes','Notifications']],
 TECHNICIAN:[['mine','Assigned to me'],['work','My workload'],['notes','Notifications']],
 ADMIN:[['dash','Dashboard'],['all','All incidents'],['techs','Technicians'],['services','Services & SLA'],['audit','Audit log'],['users','Users'],['notes','Notifications']]};
function home(){var t=TABS[S.user.role];if(!S.tab||!t.some(function(x){return x[0]==S.tab}))S.tab=t[0][0];shell()}
function shell(){save();var t=TABS[S.user.role];
 app.innerHTML='<header><b>I-ITSM</b><nav>'+t.map(function(x){return '<button class="'+(x[0]==S.tab?'on':'')+'" onclick="S.tab=\\''+x[0]+'\\';shell()">'+x[1]+'</button>'}).join('')+'</nav><span class="sp"></span><span class="mu">'+esc(S.user.name)+' ('+S.user.role+')</span><button class="b g" onclick="logout()">Logout</button></header><main id="main"></main>';
 var v=VIEWS[S.tab];if(v)v().catch(function(){})}
function main(h){document.getElementById('main').innerHTML=h}

/* ---------- incident table & detail ---------- */
function table(list){
 if(!list.length)return '<p class="mu">No incidents found.</p>';
 return '<table><tr><th>ID</th><th>Title</th><th>Service</th><th>Priority</th><th>Status</th><th>SLA</th><th>Technician</th></tr>'+list.map(function(i){
  return '<tr class="click" onclick="detail(\\''+i.id+'\\')"><td>'+esc(i.id)+'</td><td>'+esc(i.title)+'</td><td>'+esc(i.service)+'</td><td>'+tag(i.priority,i.priority)+'</td><td>'+tag('st',i.status)+'</td><td>'+tag(i.sla,i.sla)+'</td><td>'+esc(i.technician||'-')+'</td></tr>'}).join('')+'</table>'}
function closeM(){document.getElementById('modal').style.display='none'}
async function detail(id){
 var i=await api('incident',{id:id}),audit=[],techs=[];
 try{audit=await api('audit',{id:id})}catch(e){}
 var r=S.user.role,s=i.status,a='';
 if(r=='ADMIN'&&['NEW','CLASSIFIED','ASSIGNED'].indexOf(s)>=0){techs=await api('technicians');
  a+='<h3>Assign manually</h3><div class="row"><select id="tsel">'+techs.map(function(t){return '<option value="'+t.id+'">'+esc(t.name)+' (load '+t.load+'/'+t.maxLoad+')</option>'}).join('')+'</select><button class="b" onclick="act(\\'assign\\',\\''+id+'\\',{technician:val(\\'tsel\\')})">Assign</button></div>'}
 if(r=='TECHNICIAN'){
  if(s=='ASSIGNED')a+=btn('ack',id,'Acknowledge');
  if(s=='ACKNOWLEDGED')a+=btn('start',id,'Start investigation');
  if(s=='IN_PROGRESS')a+='<h3>Resolve</h3><textarea id="res" placeholder="Resolution details"></textarea><button class="b" onclick="act(\\'resolve\\',\\''+id+'\\',{text:val(\\'res\\')})">Mark resolved</button>';
  if(['ACKNOWLEDGED','IN_PROGRESS'].indexOf(s)>=0)a+='<h3>Request more information from reporter</h3><div class="row"><input id="ask"><button class="b g" onclick="act(\\'requestinfo\\',\\''+id+'\\',{text:val(\\'ask\\')})">Send</button></div>'}
 if(r!='ADMIN'||true){a+='<h3>Add note / information</h3><div class="row"><input id="nt"><button class="b g" onclick="act(\\'note\\',\\''+id+'\\',{text:val(\\'nt\\')})">Add</button></div>'}
 if(r!='TECHNICIAN'){
  if(s=='RESOLVED')a+='<h3>Verify fix</h3>'+btn('verify',id,'Accept fix',{accepted:'true'})+' '+btn('verify',id,'Reject (reopen)',{accepted:'false'},'r');
  if(s=='VERIFIED')a+=btn('close',id,'Close incident');
  if(s=='CLOSED'&&r=='EMPLOYEE'&&!i.rating)a+='<h3>Feedback</h3><div class="row"><select id="rt"><option>5</option><option>4</option><option>3</option><option>2</option><option>1</option></select><input id="fc" placeholder="Comment"><button class="b" onclick="act(\\'feedback\\',\\''+id+'\\',{rating:val(\\'rt\\'),text:val(\\'fc\\')})">Submit</button></div>'}
 document.getElementById('mbody').innerHTML='<div class="row"><h2 style="flex:1">'+esc(i.id)+' - '+esc(i.title)+'</h2><button class="b g" onclick="closeM()">Close</button></div>'+
 '<p>'+tag(i.priority,i.priority)+' '+tag('st',i.status)+' '+tag(i.sla,'SLA '+i.sla)+' '+(i.escalation?tag('P1','Escalation level '+i.escalation):'')+'</p>'+
 '<p>'+esc(i.description)+'</p><table><tr><td class="mu">Service</td><td>'+esc(i.service)+'</td><td class="mu">Category</td><td>'+esc(i.category)+'</td></tr>'+
 '<tr><td class="mu">Reporter</td><td>'+esc(i.reporter)+'</td><td class="mu">Technician</td><td>'+esc(i.technician||'-')+'</td></tr>'+
 '<tr><td class="mu">Impact / Urgency / Users</td><td>'+i.impact+' / '+i.urgency+' / '+i.users+'</td><td class="mu">Created</td><td>'+esc(i.created)+'</td></tr>'+
 '<tr><td class="mu">Response due</td><td>'+esc(i.responseDue)+'</td><td class="mu">Resolution due</td><td>'+esc(i.resolveDue)+'</td></tr></table>'+
 (i.resolution?'<h3>Resolution</h3><p>'+esc(i.resolution)+'</p>':'')+
 (i.related.length?'<h3>Possibly related incidents (duplicate / correlation detection)</h3><p>'+i.related.map(function(x){return '<a onclick="detail(\\''+x.id+'\\')">'+esc(x.id)+'</a> ('+x.score+'% similar)'}).join(', ')+'</p>':'')+
 (i.notes.length?'<h3>Investigation notes</h3>'+i.notes.map(function(n){return '<div class="note"><span class="mu">'+esc(n.at)+' - '+esc(n.by)+' ['+esc(n.kind)+']</span><br>'+esc(n.text)+'</div>'}).join(''):'')+a+
 (audit.length?'<h3>Audit trail</h3><table>'+audit.map(function(x){return '<tr><td class="mu">'+esc(x.time)+'</td><td>'+esc(x.action)+'</td><td>'+esc(x.user)+'</td><td>'+(x.from!=x.to?esc(x.from)+' &rarr; '+esc(x.to):'')+'</td><td>'+esc(x.text)+'</td></tr>'}).join('')+'</table>':'');
 document.getElementById('modal').style.display='flex'}
function btn(a,id,label,p,cls){return '<button class="b '+(cls||'')+'" onclick=\\'act("'+a+'","'+id+'",'+JSON.stringify(p||{})+')\\'>'+label+'</button>'}
async function act(a,id,p){p=p||{};p.id=id;try{await api(a,p);toast('Done');await detail(id);shell()}catch(e){}}

/* ---------- charts (plain SVG, no libraries) ---------- */
function donut(m){var cols={P1:'var(--p1)',P2:'var(--p2)',P3:'var(--p3)',P4:'var(--p4)'},tot=0,k;for(k in m)tot+=m[k];
 if(!tot)return '<p class="mu">No data yet.</p>';
 var r=40,c=2*Math.PI*r,off=0,s='<svg viewBox="0 0 120 120" width="150" height="150"><g transform="rotate(-90 60 60)">';
 for(k in m){if(m[k]){var l=m[k]/tot*c;s+='<circle cx="60" cy="60" r="'+r+'" fill="none" style="stroke:'+cols[k]+'" stroke-width="18" stroke-dasharray="'+l+' '+(c-l)+'" stroke-dashoffset="'+(-off)+'"></circle>';off+=l}}
 s+='</g><text x="60" y="66" text-anchor="middle" font-size="20" font-weight="700" fill="currentColor">'+tot+'</text></svg>';
 var lg=Object.keys(m).map(function(k){return '<div class="bar"><span>'+tag(k,k)+'</span><b>'+m[k]+'</b></div>'}).join('');
 return '<div class="row" style="align-items:center"><div style="flex:0">'+s+'</div><div>'+lg+'</div></div>'}

/* ---------- views ---------- */
function filterBar(){return '<div class="row"><div><label>Incident ID</label><input id="f_id"></div><div><label>Keyword</label><input id="f_keyword"></div>'+
 '<div><label>Status</label><select id="f_status"><option value="">Any</option>'+['NEW','CLASSIFIED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS','RESOLVED','VERIFIED','CLOSED'].map(function(x){return '<option>'+x+'</option>'}).join('')+'</select></div>'+
 '<div><label>Priority</label><select id="f_priority"><option value="">Any</option><option>P1</option><option>P2</option><option>P3</option><option>P4</option></select></div>'+
 '<div><label>Category</label><select id="f_category"></select></div><div><label>Service</label><select id="f_service"></select></div>'+
 '<div><label>SLA</label><select id="f_sla"><option value="">Any</option><option>ON_TRACK</option><option>AT_RISK</option><option>BREACHED</option></select></div>'+
 '<div><label>From</label><input id="f_from" type="date"></div><div><label>To</label><input id="f_to" type="date"></div><button class="b" onclick="runSearch()">Search</button></div>'}
async function fillFilters(){var c=await api('categories'),s=await api('services');
 document.getElementById('f_category').innerHTML='<option value="">Any</option>'+c.map(function(x){return '<option>'+x+'</option>'}).join('');
 document.getElementById('f_service').innerHTML='<option value="">Any</option>'+s.map(function(x){return '<option>'+esc(x.name)+'</option>'}).join('')}
async function runSearch(){var p={};['id','keyword','status','priority','category','service','sla','from','to'].forEach(function(k){p[k]=val('f_'+k)});
 document.getElementById('res_list').innerHTML=table(await api('incidents',p))}
var VIEWS={
 async mine(){var l=await api('incidents');main('<div class="card"><h2>'+(S.user.role=='EMPLOYEE'?'My incidents':'Incidents assigned to me')+'</h2>'+table(l)+'</div>')},
 async all(){main('<div class="card"><h2>Search incidents</h2>'+filterBar()+'</div><div class="card" id="res_list"></div>');await fillFilters();runSearch()},
 async report(){var s=await api('services'),c=await api('categories');
  main('<div class="card"><h2>Report an incident</h2><label>Title</label><input id="r_t" style="width:100%"><label>Description</label><textarea id="r_d" oninput="sugg()"></textarea>'+
  '<div class="row"><div><label>Affected service</label><select id="r_s">'+s.map(function(x){return '<option>'+esc(x.name)+'</option>'}).join('')+'</select></div>'+
  '<div><label>Category (auto-suggested)</label><select id="r_c"><option value="">Auto-detect</option>'+c.map(function(x){return '<option>'+x+'</option>'}).join('')+'</select></div>'+
  '<div><label>Impact</label><select id="r_i"><option value="1">1 Low</option><option value="2" selected>2 Medium</option><option value="3">3 High</option></select></div>'+
  '<div><label>Urgency</label><select id="r_u"><option value="1">1 Low</option><option value="2" selected>2 Medium</option><option value="3">3 High</option></select></div>'+
  '<div><label>Users affected</label><input id="r_n" type="number" min="1" value="1"></div></div><p id="sg" class="mu"></p><button class="b" onclick="submitInc()">Submit incident</button></div>')},
 async work(){var t=await api('technicians'),me=t.filter(function(x){return x.id==S.user.id})[0]||{},l=await api('incidents');
  main('<div class="card"><h2>My workload</h2><div class="stats"><div class="stat"><b>'+(me.load||0)+' / '+(me.maxLoad||0)+'</b><span>Active incidents / capacity</span></div><div class="stat"><b>'+(me.available?'Available':'Unavailable')+'</b><span>Status</span></div></div>'+
  '<p>Skills: '+((me.skills||[]).join(', '))+'</p></div><div class="card"><h2>My incidents</h2>'+table(l)+'</div>')},
 async notes(){var n=await api('notifications');main('<div class="card"><h2>Notifications</h2>'+(n.length?'<table><tr><th>Time</th><th>Channel</th><th>Subject</th><th>Message</th></tr>'+n.slice().reverse().map(function(x){return '<tr><td class="mu">'+esc(x.time)+'</td><td>'+tag('st',x.channel)+'</td><td>'+esc(x.subject)+'</td><td>'+esc(x.body)+'</td></tr>'}).join('')+'</table>':'<p class="mu">No notifications yet.</p>')+'</div>')},
 async dash(){var o=await api('overview'),w=await api('workload'),sa=await api('service-analysis'),al=await api('sla-check'),t=await api('technicians');
  var nm={};t.forEach(function(x){nm[x.id]=x.name});
  function bars(m,lab){var mx=Math.max.apply(null,Object.values(m).concat([1]));return Object.keys(m).map(function(k){return '<div class="bar"><span>'+esc(lab?lab(k):k)+'</span><i style="width:'+(m[k]/mx*60)+'%"></i><b>'+m[k]+'</b></div>'}).join('')||'<p class="mu">No data yet.</p>'}
  var st=await api('strategies'),sx=await api('stats');
  main('<div class="card"><h2>Incident overview</h2><div class="stats"><div class="stat"><b>'+o.total+'</b><span>Total</span></div><div class="stat"><b>'+o.open+'</b><span>Open</span></div><div class="stat"><b style="color:var(--p1)">'+o.critical+'</b><span>Critical (P1)</span></div><div class="stat"><b>'+o.resolved+'</b><span>Resolved</span></div><div class="stat"><b style="color:var(--p1)">'+o.slaViolations+'</b><span>SLA violations</span></div></div></div>'+
  '<div class="row" style="align-items:stretch"><div class="card"><h2>Incidents by priority</h2>'+donut(sx.priority)+'</div><div class="card"><h2>Incidents by status</h2>'+bars(sx.status)+'</div><div class="card"><h2>Incidents by category</h2>'+bars(sx.category)+'</div></div>'+
  '<div class="row" style="align-items:stretch"><div class="card"><h2>Technician workload</h2>'+bars(w,function(k){return nm[k]||k})+'</div><div class="card"><h2>Incidents per service</h2>'+bars(sa)+'</div></div>'+
  '<div class="card"><h2>SLA monitor</h2><p>'+(al.length?al.map(function(x){return tag('P2',x.kind)+' <a onclick="detail(\\''+x.incident+'\\')">'+esc(x.incident)+'</a>'}).join(' '):'<span class="mu">No new warnings or violations.</span>')+'</p></div>'+
  '<div class="card"><h2>Strategies in use</h2><div class="row"><div><label>Technician assignment</label><select id="sa"><option value="skill">Skill-based</option><option value="load">Load-based</option><option value="round_robin">Round-robin</option></select></div>'+
  '<div><label>Priority calculation</label><select id="sp"><option value="score">Score (impact x urgency x criticality)</option><option value="weighted">Weighted</option></select></div><button class="b" onclick="setStrat()">Apply</button></div><p class="mu">Current: assignment='+esc(st.assignment)+', priority='+esc(st.priority)+'</p></div>');
  document.getElementById('sa').value=st.assignment;document.getElementById('sp').value=st.priority},
 async techs(){var t=await api('technicians');
  main('<div class="card"><h2>Technicians</h2><table><tr><th>Name</th><th>Skills</th><th>Years</th><th>Load</th><th>Available</th></tr>'+t.map(function(x){return '<tr><td>'+esc(x.name)+' <span class="mu">'+x.id+'</span></td><td>'+x.skills.join(', ')+'</td><td>'+x.years+'</td><td>'+x.load+' / '+x.maxLoad+'</td><td><button class="b g" onclick="avail(\\''+x.id+'\\','+!x.available+')">'+(x.available?'Yes - set unavailable':'No - set available')+'</button></td></tr>'}).join('')+'</table></div>')},
 async services(){var s=await api('services');
  main('<div class="card"><h2>IT services</h2><table><tr><th>Service</th><th>Business criticality (1-3)</th></tr>'+s.map(function(x){return '<tr><td>'+esc(x.name)+'</td><td>'+x.criticality+'</td></tr>'}).join('')+'</table>'+
  '<div class="row"><div><label>New service</label><input id="ns"></div><div><label>Criticality</label><select id="nc"><option>1</option><option>2</option><option selected>3</option></select></div><button class="b" onclick="addSvc()">Add</button></div></div>'+
  '<div class="card"><h2>Configure SLA policy (minutes)</h2><div class="row"><div><label>Priority</label><select id="sl_p"><option>P1</option><option>P2</option><option>P3</option><option>P4</option></select></div><div><label>Response</label><input id="sl_r" type="number" value="5"></div><div><label>Resolution</label><input id="sl_s" type="number" value="30"></div><button class="b" onclick="cfgSla()">Save</button></div><p class="mu">Defaults: P1 5/30 min, P2 15/120, P3 60/480, P4 240/1440</p></div>')},
 async audit(){var a=await api('audit');main('<div class="card"><h2>Audit log (all incidents)</h2>'+(a.length?'<table><tr><th>Time</th><th>Incident</th><th>Action</th><th>User</th><th>Transition</th><th>Details</th></tr>'+a.slice().reverse().map(function(x){return '<tr><td class="mu">'+esc(x.time)+'</td><td>'+esc(x.incident)+'</td><td>'+esc(x.action)+'</td><td>'+esc(x.user)+'</td><td>'+(x.from!=x.to?esc(x.from)+' &rarr; '+esc(x.to):'')+'</td><td>'+esc(x.text)+'</td></tr>'}).join('')+'</table>':'<p class="mu">Empty.</p>')+'</div>')},
 async users(){var u=await api('users');
  main('<div class="card"><h2>Users</h2><table><tr><th>ID</th><th>Name</th><th>Email</th><th>Role</th></tr>'+u.map(function(x){return '<tr><td>'+x.id+'</td><td>'+esc(x.name)+'</td><td>'+esc(x.email)+'</td><td>'+x.role+'</td></tr>'}).join('')+'</table></div>'+
  '<div class="card"><h2>Add user</h2><div class="row"><div><label>Name</label><input id="u_n"></div><div><label>Email</label><input id="u_e"></div><div><label>Password (min 8)</label><input id="u_p" type="password"></div>'+
  '<div><label>Role</label><select id="u_r"><option>TECHNICIAN</option><option>EMPLOYEE</option><option>ADMIN</option></select></div></div>'+
  '<div class="row"><div><label>Technician skills (comma separated, e.g. NETWORK,DATABASE)</label><input id="u_s"></div><div><label>Years of experience</label><input id="u_y" type="number" value="1"></div><div><label>Max load</label><input id="u_m" type="number" value="5"></div><button class="b" onclick="addUser()">Add</button></div></div>')}};
async function sugg(){var t=val('r_d');if(t.length<8){document.getElementById('sg').textContent='';return}try{var d=await api('suggest-category',{text:t});document.getElementById('sg').textContent='Suggested category: '+d.category}catch(e){}}
async function submitInc(){try{var i=await api('create',{title:val('r_t'),description:val('r_d'),service:val('r_s'),category:val('r_c'),impact:val('r_i'),urgency:val('r_u'),users:val('r_n')});
 toast('Incident '+i.id+' created: '+i.priority+', '+(i.technician?'assigned to '+i.technician:'awaiting assignment'));S.tab='mine';shell();detail(i.id)}catch(e){}}
async function setStrat(){try{await api('set-strategy',{type:'assignment',name:val('sa')});await api('set-strategy',{type:'priority',name:val('sp')});toast('Strategies updated');shell()}catch(e){}}
async function avail(id,v){try{await api('availability',{technician:id,available:v});shell()}catch(e){}}
async function addSvc(){try{await api('add-service',{name:val('ns'),criticality:val('nc')});toast('Service added');shell()}catch(e){}}
async function cfgSla(){try{await api('config-sla',{priority:val('sl_p'),response:val('sl_r'),resolution:val('sl_s')});toast('SLA updated')}catch(e){}}
async function addUser(){try{await api('add-user',{name:val('u_n'),email:val('u_e'),password:val('u_p'),role:val('u_r'),skills:val('u_s'),years:val('u_y'),maxLoad:val('u_m')});toast('User added');shell()}catch(e){}}

var old=sessionStorage.getItem('iitsm');
if(old){try{var o=JSON.parse(old);S.user=o.u;S.token=o.t;S.tab=o.tab;home()}catch(e){loginView()}}else loginView();
setInterval(function(){if(S.user&&S.user.role=='ADMIN'&&S.tab=='dash')VIEWS.dash().catch(function(){})},15000);
</script></body></html>
""";
}
