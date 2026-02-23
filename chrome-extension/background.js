const GOOGLE_CLIENT_ID =
  "469407659650-5291ms90hc5v4fgjuvs9455eebrckiqp.apps.googleusercontent.com";

// ── 단축키 처리 ──
chrome.commands.onCommand.addListener(async (command) => {
  if (command !== "send-url") return;

  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.url) {
    showBadge("!", "#F44336");
    return;
  }

  const baseUrl = await getBaseUrl();
  if (!baseUrl) {
    showBadge("!", "#F44336");
    return;
  }

  const accessToken = await getValidToken(baseUrl);
  if (!accessToken) {
    showBadge("!", "#FF9800");
    chrome.runtime.openOptionsPage();
    return;
  }

  await sendUrl(baseUrl, accessToken, tab.url);
});

// ── 팝업/옵션 페이지에서 오는 메시지 처리 ──
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.action === "googleLogin") {
    handleGoogleLogin().then(sendResponse);
    return true;
  }
  if (message.action === "sendUrl") {
    handleSendUrl().then(sendResponse);
    return true;
  }
});

async function handleGoogleLogin() {
  const { serverUrl } = await chrome.storage.sync.get({
    serverUrl: "https://link-mind.gold-grit.com",
  });

  const redirectUrl = chrome.identity.getRedirectURL();
  const authUrl =
    "https://accounts.google.com/o/oauth2/v2/auth?" +
    `client_id=${encodeURIComponent(GOOGLE_CLIENT_ID)}&` +
    `redirect_uri=${encodeURIComponent(redirectUrl)}&` +
    "response_type=code&" +
    "scope=openid%20email%20profile&" +
    "access_type=offline&" +
    "prompt=consent";

  try {
    const responseUrl = await chrome.identity.launchWebAuthFlow({
      url: authUrl,
      interactive: true,
    });

    const code = new URL(responseUrl).searchParams.get("code");
    if (!code) return { success: false, error: "No authorization code" };

    const baseUrl = serverUrl.replace(/\/+$/, "");
    const response = await fetch(`${baseUrl}/api/auth/google`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code, redirectUri: redirectUrl }),
    });

    if (!response.ok) {
      return { success: false, error: `Server error (${response.status})` };
    }

    const result = await response.json();
    const accessToken = result?.data?.accessToken;
    const refreshToken = result?.data?.refreshToken;
    if (!accessToken || !refreshToken) {
      return { success: false, error: "Invalid server response" };
    }

    await chrome.storage.local.set({ accessToken, refreshToken });
    return { success: true };
  } catch (error) {
    return { success: false, error: error?.message || "Login failed" };
  }
}

async function handleSendUrl() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.url) return { success: false, error: "Cannot read page URL" };

  const baseUrl = await getBaseUrl();
  if (!baseUrl) return { success: false, error: "Invalid server URL" };

  const accessToken = await getValidToken(baseUrl);
  if (!accessToken) return { success: false, error: "Not logged in" };

  return sendUrl(baseUrl, accessToken, tab.url);
}

// C3: 401 시 refresh 재시도 후 재요청
async function sendUrl(baseUrl, accessToken, url) {
  try {
    let response = await fetchWithTimeout(`${baseUrl}/api/urls`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({ url }),
    });

    // 401이면 토큰 갱신 후 1회 재시도
    if (response.status === 401) {
      const newToken = await refreshToken(baseUrl);
      if (newToken) {
        response = await fetchWithTimeout(`${baseUrl}/api/urls`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${newToken}`,
          },
          body: JSON.stringify({ url }),
        });
      }
    }

    if (response.ok) {
      showBadge("\u2713", "#4CAF50");
      return { success: true };
    } else if (response.status === 401) {
      await chrome.storage.local.remove(["accessToken", "refreshToken"]);
      showBadge("!", "#FF9800");
      return { success: false, error: "Session expired", expired: true };
    } else {
      showBadge(`${response.status}`, "#F44336");
      return { success: false, error: `Server error (${response.status})` };
    }
  } catch (error) {
    console.error("Link Mind: request failed", error);
    showBadge("\u2717", "#F44336");
    return { success: false, error: "Network error" };
  }
}

// ── Helpers ──

async function fetchWithTimeout(url, options, timeoutMs = 10000) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timeoutId);
  }
}

async function getBaseUrl() {
  const { serverUrl } = await chrome.storage.sync.get({
    serverUrl: "https://link-mind.gold-grit.com",
  });
  try {
    const parsed = new URL(serverUrl);
    if (!["http:", "https:"].includes(parsed.protocol)) return null;
    return parsed.origin;
  } catch {
    return null;
  }
}

function isTokenExpired(token, bufferSeconds = 60) {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return payload.exp * 1000 < Date.now() + bufferSeconds * 1000;
  } catch {
    return true;
  }
}

// C2: refresh 요청 중복 방지 (mutex)
let refreshPromise = null;

async function getValidToken(baseUrl) {
  const { accessToken, refreshToken } = await chrome.storage.local.get({
    accessToken: "",
    refreshToken: "",
  });

  if (!accessToken) return null;
  if (!isTokenExpired(accessToken)) return accessToken;
  if (!refreshToken) return null;

  return doRefresh(baseUrl, refreshToken);
}

// 401 재시도용 — 강제로 refresh 실행
async function refreshToken(baseUrl) {
  const { refreshToken } = await chrome.storage.local.get({ refreshToken: "" });
  if (!refreshToken) return null;
  return doRefresh(baseUrl, refreshToken);
}

// 실제 refresh 로직 (동시 호출 시 하나만 실행)
async function doRefresh(baseUrl, token) {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const response = await fetchWithTimeout(`${baseUrl}/api/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: token }),
      });

      if (!response.ok) {
        await chrome.storage.local.remove(["accessToken", "refreshToken"]);
        return null;
      }

      const result = await response.json();
      const newAccess = result?.data?.accessToken;
      const newRefresh = result?.data?.refreshToken;
      if (!newAccess || !newRefresh) return null;

      await chrome.storage.local.set({
        accessToken: newAccess,
        refreshToken: newRefresh,
      });
      return newAccess;
    } catch {
      return null;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

let badgeTimerId = null;

function showBadge(text, color) {
  if (badgeTimerId) clearTimeout(badgeTimerId);
  chrome.action.setBadgeText({ text });
  chrome.action.setBadgeBackgroundColor({ color });
  badgeTimerId = setTimeout(() => {
    chrome.action.setBadgeText({ text: "" });
    badgeTimerId = null;
  }, 3000);
}
