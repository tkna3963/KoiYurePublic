// ========================================
// 地図グローバル変数
// ========================================
let isMapMode = false;
let LeafletMapSet = null;

// 地図データ
let PrefArea;
let SaibunArea;
let TsunamiArea;
let PrefGeoJSON;
let SaibunGeoJSON;
let TsunamiGeoJSON;
let EpspAreaData = null;

// マーカー類
const areaMarkers   = new Map();
let EpicenterMarker;
const marker9611Map = new Map();
let shindoMarkers   = new Map();
let userLocationMarker = null;

// ========================================
// ユーザー位置マーカーを追加
// ========================================
function addUserLocationMarker() {
    if (!LeafletMapSet || !userLocation) return;

    // 既存のマーカーがあれば削除
    if (userLocationMarker) {
        LeafletMapSet.removeLayer(userLocationMarker);
    }

    userLocationMarker = L.marker([userLocation.lat, userLocation.lon])
        .addTo(LeafletMapSet)
        .bindPopup(`あなたの現在地: ${userLocation.place}<br>(${userLocation.lat.toFixed(2)}, ${userLocation.lon.toFixed(2)})`)
        .openPopup();

    LeafletMapSet.setView([userLocation.lat, userLocation.lon], 10);
}

// ========================================
// 地図と背景の切り替え
// ========================================
async function toggleMapMode() {
    isMapMode = !isMapMode;

    if (isMapMode) {
        // 地図モードへ
        elements.backImg.style.display    = 'none';
        elements.leafletMap.style.display = 'block';
        elements.koisiImg.style.height    = '20vh';
        elements.koisiarrow.style.display = 'none';

        if (typeof KoishiFaceUpdate === 'function') {
            KoishiFaceUpdate('Img/koisiyukuri.png');
        }

        // 地図の初期化（初回のみ）
        if (!LeafletMapSet && typeof L !== 'undefined') {
            try {
                LeafletMapSet = L.map('LeafletMap').setView([35.681236, 139.767125], 5);

                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                }).addTo(LeafletMapSet);

                // GeoJSONデータの読み込み
                PrefArea    = await fetch("Item/Pref.geojson").then(res => res.json());
                SaibunArea  = await fetch("Item/Saibun.geojson").then(res => res.json());
                TsunamiArea = await fetch("Item/Tsunami.geojson").then(res => res.json());

                PrefGeoJSON    = L.geoJSON(PrefArea,    { style: { color: "green", fillOpacity: 0.0 } }).addTo(LeafletMapSet);
                SaibunGeoJSON  = L.geoJSON(SaibunArea,  { style: { color: "red",   fillOpacity: 0.0 } }).addTo(LeafletMapSet);
                TsunamiGeoJSON = L.geoJSON(TsunamiArea, { style: { color: "blue",  fillOpacity: 0.0 } }).addTo(LeafletMapSet);
                
                addUserLocationMarker();
            } catch (error) {
                console.error("地図初期化エラー:", error);
                LeafletMapSet = null;  // エラー時は初期化状態をリセット
            }

        } else if (LeafletMapSet) {
            // 既に初期化済みの場合はマーカーだけ更新
            try {
                addUserLocationMarker();
            } catch (error) {
                console.error("マーカー追加エラー:", error);
            }
        }

    } else {
        // 通常モードへ
        elements.backImg.style.display    = 'block';
        elements.leafletMap.style.display = 'none';
        elements.koisiImg.style.height    = '55vh';
        elements.koisiarrow.style.display = 'block';

        if (typeof KoishiFaceUpdate === 'function') {
            KoishiFaceUpdate('Img/hyoujyou_bishou_koisi.png');
        }
    }
}

// ========================================
// P2P地震情報を地図に反映
// ========================================
function P2PMap(msg) {
    if (!LeafletMapSet) return;
    
    const code = msg.code;
    
    // 地震情報（551）: 震源マーカーを配置
    if (code === 551) {
        const quake = msg.earthquake ?? {};
        const hypo = quake.hypocenter ?? {};
        const lat = hypo.latitude;
        const lon = hypo.longitude;
        
        if (lat && lon && lat > -100) {
            // 既存のマーカーを削除（最新のもののみ表示）
            if (EpicenterMarker) {
                LeafletMapSet.removeLayer(EpicenterMarker);
            }
            
            const name = hypo.name ?? '不明な震源';
            const mag = hypo.magnitude ?? '?';
            EpicenterMarker = L.marker([lat, lon])
                .addTo(LeafletMapSet)
                .bindPopup(`${name}<br>M${mag}`)
                .openPopup();
        }
    }
    
    // 感知情報（561/9611）: 地域マーカーを配置
    if (code === 9611) {
        const areaConf = msg.area_confidences ?? {};
        
        // 古いマーカーを削除
        marker9611Map.forEach(m => LeafletMapSet.removeLayer(m));
        marker9611Map.clear();
        
        // 新しいマーカーを追加
        for (const [key, ac] of Object.entries(areaConf)) {
            const areaCode = Math.round(parseFloat(key));
            if (typeof EpspArea !== 'undefined') {
                const latLon = EpspArea.latLonOf(areaCode);
                if (latLon) {
                    const areaName = EpspArea.nameOf(areaCode);
                    const marker = L.marker([latLon[0], latLon[1]], { opacity: 0.6 })
                        .addTo(LeafletMapSet)
                        .bindPopup(`${areaName}<br>${ac.display ?? '-'}`);
                    marker9611Map.set(areaCode, marker);
                }
            }
        }
    }
}