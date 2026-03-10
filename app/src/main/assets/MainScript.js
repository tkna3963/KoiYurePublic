// ========================================
// グローバル変数
// ========================================
let AllWebsocketData = [];
let timeUpdateInterval = null;

// ユーザー位置情報を保存
let userLocation = null;

// ========================================
// DOM要素のキャッシュ
// ========================================
const elements = {};

function cacheElements() {
    elements.nowTime        = document.getElementById('now_time');
    elements.textarea       = document.getElementById('maintextarea');
    elements.pagebar        = document.getElementById('pagebar');
    elements.menu           = document.getElementById('menu');
    elements.infomenu       = document.getElementById('infomenu');
    elements.toggleMenuBtn  = document.getElementById('toggleMenuBtn');
    elements.toggleInfoMenuBtn = document.getElementById('toggleinfoMenuBtn');
    elements.mapChangeBtn   = document.getElementById('MapChange');
    elements.backImg        = document.getElementById('back');
    elements.koisiImg       = document.getElementById('koisi');
    elements.leafletMap     = document.getElementById('LeafletMap');
    elements.koisiarrow     = document.querySelector('.koisiarrow_box');
    elements.p2pIndicator   = document.getElementById('p2pStatusIndicator');
    elements.p2pText        = document.getElementById('p2pStatusText');
    elements.wolfxIndicator = document.getElementById('wolfxStatusIndicator');
    elements.wolfxText      = document.getElementById('wolfxStatusText');
    elements.nowLocate      = document.getElementById('NowLocate');
}

// ========================================
// 時計を動かす
// ========================================
function updateTime() {
    const now = new Date();
    const formatted =
        `${now.getFullYear()}/${String(now.getMonth() + 1).padStart(2, '0')}/${String(now.getDate()).padStart(2, '0')} ` +
        `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
    if (elements.nowTime) {
        elements.nowTime.textContent = formatted;
    }
}

// ========================================
// メニュー開閉
// ========================================
function toggleMenu(menuElement, btnElement) {
    const isOpen = menuElement.classList.toggle('open');
    if (btnElement) {
        btnElement.setAttribute('aria-expanded', isOpen);
    }
}

// ========================================
// テキストエリア更新
// ========================================
function updateMainTextarea(index) {
    if (!elements.textarea || index < 0 || index >= AllWebsocketData.length) return;

    let data = AllWebsocketData[index];

    if (typeof data === 'string') {
        try {
            data = JSON.parse(data);
        } catch (e) {
            elements.textarea.value = '(データの読み込みに失敗しました)';
            return;
        }
    }

    if (data.code && typeof formatToTelop === 'function') {
        elements.textarea.value = formatToTelop(data);
    } else if (data.type && typeof wolfxcoverter === 'function') {
        elements.textarea.value = wolfxcoverter(data) ?? '(空のメッセージ)';
    } else {
        elements.textarea.value = '(表示できる情報がありません)';
    }
}

// ========================================
// ページバー更新
// ========================================
function updatePagebar() {
    if (!elements.pagebar) return;
    const maxIndex = Math.max(0, AllWebsocketData.length - 1);
    elements.pagebar.max   = maxIndex;
    elements.pagebar.value = maxIndex;
}

// ========================================
// WebSocketメッセージ受信
// ========================================
function onP2PMessage(msg) {
    AllWebsocketData.push(msg);
    updatePagebar();
    if (typeof P2PMap === 'function') {
        P2PMap(msg);
    }
    updateMainTextarea(AllWebsocketData.length - 1);
}

function onWolfxMessage(msg) {
    AllWebsocketData.push(msg);
    updatePagebar();
    updateMainTextarea(AllWebsocketData.length - 1);
}

// ========================================
// 接続状態表示更新
// ========================================
function updateConnectionStatus(indicator, textElement, isConnected) {
    if (!indicator || !textElement) return;

    if (isConnected) {
        indicator.classList.remove('status-disconnected');
        indicator.classList.add('status-connected');
        textElement.textContent = '接続中';
    } else {
        indicator.classList.remove('status-connected');
        indicator.classList.add('status-disconnected');
        textElement.textContent = '切断';
    }
}

function onP2PStatusChange(isConnectedStr) {
    updateConnectionStatus(elements.p2pIndicator, elements.p2pText, isConnectedStr === 'true');
}

function onWolfxStatusChange(isConnectedStr) {
    updateConnectionStatus(elements.wolfxIndicator, elements.wolfxText, isConnectedStr === 'true');
}

// ========================================
// 位置情報取得
// ========================================
function getUserLocation() {
    navigator.geolocation.getCurrentPosition(
        function (position) {
            const latitude  = position.coords.latitude;
            const longitude = position.coords.longitude;

            fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${latitude}&lon=${longitude}`)
                .then(response => response.json())
                .then(data => {
                    const place = data.address.city || data.address.town || data.address.village || "不明な地域";

                    userLocation = { lat: latitude, lon: longitude, place: place };

                    if (elements.nowLocate) {
                        elements.nowLocate.textContent = `${place} (${latitude.toFixed(1)}, ${longitude.toFixed(1)})`;
                    }

                    // 地図が既に初期化されていればマーカーを追加
                    if (typeof addUserLocationMarker === 'function') {
                        addUserLocationMarker();
                    }
                })
                .catch(error => {
                    console.error("逆ジオコーディングに失敗:", error);
                    if (elements.nowLocate) elements.nowLocate.textContent = "位置情報の取得に失敗";
                });
        },
        function (error) {
            let errorMsg = "位置情報エラー";
            switch (error.code) {
                case error.PERMISSION_DENIED:   errorMsg = "位置情報の許可が必要です"; break;
                case error.POSITION_UNAVAILABLE: errorMsg = "位置情報が利用できません"; break;
                case error.TIMEOUT:             errorMsg = "位置情報の取得がタイムアウト"; break;
                default:                        errorMsg = "不明なエラー"; break;
            }
            if (elements.nowLocate) elements.nowLocate.textContent = errorMsg;
        }
    );
}

// ========================================
// アプリ初期化
// ========================================
function initializeApp() {
    cacheElements();

    updateTime();
    timeUpdateInterval = setInterval(updateTime, 1000);

    if (elements.toggleMenuBtn && elements.menu) {
        elements.toggleMenuBtn.addEventListener('click', () => toggleMenu(elements.menu, elements.toggleMenuBtn));
    }

    if (elements.toggleInfoMenuBtn && elements.infomenu) {
        elements.toggleInfoMenuBtn.addEventListener('click', () => toggleMenu(elements.infomenu, elements.toggleInfoMenuBtn));
    }

    if (elements.pagebar) {
        elements.pagebar.addEventListener('input', (e) => updateMainTextarea(parseInt(e.target.value, 10)));
    }

    if (elements.mapChangeBtn) {
        elements.mapChangeBtn.addEventListener('click', toggleMapMode);
    }

    // 位置情報を取得
    getUserLocation();

    // Androidアプリとの連携確認
    if (typeof Android !== 'undefined' && Android !== null) {
        updateConnectionStatus(elements.p2pIndicator, elements.p2pText, Android.isP2PWebSocketConnected());
        updateConnectionStatus(elements.wolfxIndicator, elements.wolfxText, Android.isWolfxWebSocketConnected());
    } else {
        if (elements.p2pText)   elements.p2pText.textContent   = 'Android API利用不可';
        if (elements.wolfxText) elements.wolfxText.textContent = 'Android API利用不可';
    }
}

// ========================================
// ページ読み込み時に実行
// ========================================
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeApp);
} else {
    initializeApp();
}

window.addEventListener('beforeunload', () => {
    if (timeUpdateInterval) clearInterval(timeUpdateInterval);
});