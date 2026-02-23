const serverUrlInput = document.getElementById("serverUrl");
const saveServerBtn = document.getElementById("saveServer");
const serverStatusDiv = document.getElementById("serverStatus");
const googleLoginBtn = document.getElementById("googleLogin");
const logoutBtn = document.getElementById("logout");
const authStatusDiv = document.getElementById("authStatus");

// ── Load saved settings ──
chrome.storage.sync.get(
  { serverUrl: "https://link-mind.gold-grit.com" },
  (items) => {
    serverUrlInput.value = items.serverUrl;
  }
);
updateLoginStatus();

// ── Server URL save ──
saveServerBtn.addEventListener("click", () => {
  const serverUrl = serverUrlInput.value.trim();

  if (!serverUrl) {
    showStatus(serverStatusDiv, "Server URL is required.", "error");
    return;
  }

  try {
    const parsed = new URL(serverUrl);
    if (!["http:", "https:"].includes(parsed.protocol)) {
      showStatus(serverStatusDiv, "Server URL must use http or https.", "error");
      return;
    }
  } catch {
    showStatus(serverStatusDiv, "Invalid server URL format.", "error");
    return;
  }

  chrome.storage.sync.set({ serverUrl }, () => {
    showStatus(serverStatusDiv, "Server URL saved.", "success");
  });
});

// ── Google OAuth login → background.js에 위임 ──
googleLoginBtn.addEventListener("click", async () => {
  showStatus(authStatusDiv, "Logging in...", "");
  const result = await chrome.runtime.sendMessage({ action: "googleLogin" });

  if (result?.success) {
    showStatus(authStatusDiv, "Login successful!", "success");
  } else {
    showStatus(authStatusDiv, result?.error || "Login failed.", "error");
  }
  updateLoginStatus();
});

// ── Logout ──
logoutBtn.addEventListener("click", async () => {
  await chrome.storage.local.remove(["accessToken", "refreshToken"]);
  showStatus(authStatusDiv, "Logged out.", "success");
  updateLoginStatus();
});

// ── Helpers ──
async function updateLoginStatus() {
  const { accessToken } = await chrome.storage.local.get({ accessToken: "" });
  const statusDiv = document.getElementById("loginStatus");
  const loginActions = document.getElementById("loginActions");
  const logoutActions = document.getElementById("logoutActions");

  if (accessToken) {
    let label = "Logged in";
    try {
      const payload = JSON.parse(atob(accessToken.split(".")[1]));
      if (payload.email) label = `Logged in (${payload.email})`;
    } catch { /* ignore */ }

    statusDiv.textContent = label;
    statusDiv.className = "login-status logged-in";
    loginActions.style.display = "none";
    logoutActions.style.display = "block";
  } else {
    statusDiv.textContent = "Not logged in";
    statusDiv.className = "login-status logged-out";
    loginActions.style.display = "block";
    logoutActions.style.display = "none";
  }
}

function showStatus(element, message, type) {
  element.textContent = message;
  element.className = `status ${type}`;
  if (type) {
    setTimeout(() => {
      element.textContent = "";
      element.className = "status";
    }, 3000);
  }
}
