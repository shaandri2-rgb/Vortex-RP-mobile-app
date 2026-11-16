(function(){
"use strict";
var notice=document.getElementById("notice");
function showNotice(message){notice.textContent=String(message||"");notice.classList.add("on");}
function action(name){
 if(window.Cef&&typeof Cef.sendEvent==="function"){Cef.sendEvent("vortex_menu_action",name);document.getElementById("state").textContent="CLIENT BRIDGE CONNECTED";}
 else{showNotice("Preview only. In-game interaction requires the CEF library inside the SA-MP mobile client.");}
}
document.querySelectorAll("[data-action]").forEach(function(b){b.addEventListener("click",function(){action(b.getAttribute("data-action"));});});
if(window.Cef&&typeof Cef.registerEventCallback==="function"){
 Cef.registerEventCallback("vortex_notify","showNotice");
 Cef.registerEventCallback("vortex_player_name",function(v){document.getElementById("player-name").textContent=String(v);});
 Cef.registerEventCallback("vortex_money",function(v){document.getElementById("money").textContent="$"+Number(v||0).toLocaleString();});
 Cef.registerEventCallback("vortex_health",function(v){document.getElementById("health").textContent=Math.round(Number(v||0))+"%";});
 Cef.registerEventCallback("vortex_armour",function(v){document.getElementById("armour").textContent=Math.round(Number(v||0))+"%";});
 document.getElementById("state").textContent="GAME CLIENT BRIDGE";
}
window.showNotice=showNotice;
})();