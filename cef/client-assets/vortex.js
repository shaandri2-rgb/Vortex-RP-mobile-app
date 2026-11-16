(function () {
  "use strict";

  const byId = (id) => document.getElementById(id);
  const bridgeStatus = byId("bridge-status");
  const notice = byId("notice");

  function showNotice(message) {
    notice.textContent = String(message || "");
    notice.classList.add("visible");
  }

  function sendAction(action) {
    if (window.Cef && typeof Cef.sendEvent === "function") {
      Cef.sendEvent("vortex_menu_action", action);
      bridgeStatus.textContent = "GAME CLIENT CONNECTED";
    } else {
      bridgeStatus.textContent = "CEF BRIDGE NOT CONNECTED";
      showNotice("Preview only: connect this page to the SA-MP Mobile CEF client.");
    }
  }

  function updatePlayerName(value) {
    byId("player-name").textContent = String(value || "PLAYER");
  }
  function updateMoney(value) {
    const amount = Number(value);
    byId("money").textContent = "$" + (Number.isFinite(amount) ? amount.toLocaleString() : "0");
  }
  function updateHealth(value) {
    const number = Number(value);
    byId("health").textContent = (Number.isFinite(number) ? Math.round(number) : 0) + "%";
  }
  function updateArmour(value) {
    const number = Number(value);
    byId("armour").textContent = (Number.isFinite(number) ? Math.round(number) : 0) + "%";
  }

  document.querySelectorAll("[data-action]").forEach((button) => {
    button.addEventListener("click", () => sendAction(button.dataset.action));
  });

  if (window.Cef && typeof Cef.registerEventCallback === "function") {
    Cef.registerEventCallback("vortex_notify", "showNotice");
    Cef.registerEventCallback("vortex_player_name", "updatePlayerName");
    Cef.registerEventCallback("vortex_money", "updateMoney");
    Cef.registerEventCallback("vortex_health", "updateHealth");
    Cef.registerEventCallback("vortex_armour", "updateArmour");
    bridgeStatus.textContent = "GAME CLIENT CONNECTED";
  } else {
    bridgeStatus.textContent = "BROWSER PREVIEW";
  }

  window.showNotice = showNotice;
  window.updatePlayerName = updatePlayerName;
  window.updateMoney = updateMoney;
  window.updateHealth = updateHealth;
  window.updateArmour = updateArmour;
}());
