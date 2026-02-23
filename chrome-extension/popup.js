const loggedOutView = document.getElementById("loggedOutView");
const loggedInView = document.getElementById("loggedInView");
const userInfo = document.getElementById("userInfo");
const statusMsg = document.getElementById("statusMsg");
const logoutBtn = document.getElementById("logout");

updateView();

// ── Google Login → background.js에 위임 ──
document.getElementById("googleLogin").addEventListener("click", async () => {
  showStatus("Logging in...", "");
  const result = await chrome.runtime.sendMessage({ action: "googleLogin" });

  if (result?.success) {
    showStatus("Login successful!", "success");
    updateView();
  } else {
    showStatus(result?.error || "Login failed.", "error");
  }
});

// ── Send URL → background.js에 위임 ──
document.getElementById("sendUrl").addEventListener("click", async () => {
  const result = await chrome.runtime.sendMessage({ action: "sendUrl" });

  if (result?.success) {
    showStatus("Saved!", "success");
  } else if (result?.expired) {
    showStatus("Session expired. Please login again.", "error");
    updateView();
  } else {
    showStatus(result?.error || "Failed.", "error");
  }
});

// ── Logout ──
logoutBtn.addEventListener("click", async () => {
  await chrome.storage.local.remove(["accessToken", "refreshToken"]);
  showStatus("Logged out.", "success");
  updateView();
});

// ── Settings ──
document.getElementById("openSettings").addEventListener("click", () => {
  chrome.runtime.openOptionsPage();
});

// ── Helpers ──
async function updateView() {
  const { accessToken } = await chrome.storage.local.get({ accessToken: "" });

  if (accessToken) {
    loggedOutView.style.display = "none";
    loggedInView.style.display = "block";
    logoutBtn.style.display = "block";

    try {
      const payload = JSON.parse(atob(accessToken.split(".")[1]));
      userInfo.textContent = payload.email || "Logged in";
    } catch {
      userInfo.textContent = "Logged in";
    }
  } else {
    loggedOutView.style.display = "block";
    loggedInView.style.display = "none";
    logoutBtn.style.display = "none";
    userInfo.textContent = "";
  }
}

function showStatus(message, type) {
  statusMsg.textContent = message;
  statusMsg.className = `status-msg ${type}`;
  if (type) {
    setTimeout(() => {
      statusMsg.textContent = "";
      statusMsg.className = "status-msg";
    }, 3000);
  }
}
