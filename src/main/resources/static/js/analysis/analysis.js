// analysis.js

// 카테고리별 고정 컬러 맵
const categoryColors = {
    "식비": "#ff6b6b",
    "카페/간식": "#a67c52",
    "문화/레저": "#845ef7",
    "교통": "#339af0",
    "비품/기타": "#94a3b8",
    "기본": "#e2e8f0"
};

// 현재 선택된 그룹 ID
let currentGroupId = null;

// Chart 인스턴스 관리 (재생성 전 반드시 destroy)
let expenseChartInstance = null;
let recentGaugeInstance = null;

// =====================================================================
// [신규] 내 그룹 목록 로드 → 드롭다운 세팅 → 첫 번째 그룹 자동 분석
// =====================================================================
async function loadMyGroupsForAnalysis() {
    try {
        const res = await authFetch('/api/group/my-groups', {
            credentials: 'include'
        });
        if (!res.ok) throw new Error('그룹 목록 로드 실패');
        const groups = await res.json();

        const selector = document.getElementById('group-selector');
        if (!selector) return;

        if (!groups || groups.length === 0) {
            selector.innerHTML = '<option value="">소속 그룹 없음</option>';
            showError('소속된 그룹이 없습니다.');
            return;
        }

        selector.innerHTML = groups.map(g =>
            `<option value="${g.groupId}">${g.groupName}</option>`
        ).join('');

        // 첫 번째 그룹으로 자동 분석 시작
        currentGroupId = groups[0].groupId;
        selector.value = currentGroupId;
        fetchAnalysisData(currentGroupId);

    } catch (e) {
        showError('그룹 정보를 불러오지 못했습니다.');
    }
}

// 드롭다운 변경 시 호출
function onGroupChange(groupId) {
    if (!groupId) return;
    currentGroupId = parseInt(groupId);

    // 기존 차트 인스턴스 파괴 (Canvas 재사용 오류 방지)
    destroyCharts();

    // 로딩 상태로 전환
    document.getElementById('loading-skeleton').classList.remove('hidden');
    document.getElementById('analysis-content').classList.add('hidden');

    fetchAnalysisData(currentGroupId);
}

// 차트 인스턴스 일괄 파괴
function destroyCharts() {
    if (expenseChartInstance) {
        expenseChartInstance.destroy();
        expenseChartInstance = null;
    }
    if (recentGaugeInstance) {
        recentGaugeInstance.destroy();
        recentGaugeInstance = null;
    }
    // window.myRecentGauge 도 초기화 (하위 호환)
    if (window.myRecentGauge) {
        window.myRecentGauge.destroy();
        window.myRecentGauge = null;
    }
}
// =====================================================================

// 1. 서버에서 분석 데이터를 가져오는 함수 (groupId 파라미터 추가)
async function fetchAnalysisData(groupId) {
    try {
        const url = groupId ? `/api/analysis?groupId=${groupId}` : '/api/analysis';
        const response = await authFetch(url, {
            credentials: 'include'
        });

        if (!response.ok) throw new Error('Network response was not ok');

        const data = await response.json();
        updateUI(data);
    } catch (error) {
        const recentHeadline = document.getElementById('recent-headline');
        if (recentHeadline) recentHeadline.innerText = "분석 로드 실패";

        console.error('분석 실패:', error);
        document.getElementById('ai-headline').innerText = "데이터 로딩 실패";
        document.getElementById('ai-commentary').innerText = "서버와 통신하는 중 오류가 발생했습니다.";

        document.getElementById('loading-skeleton').classList.add('hidden');
        document.getElementById('analysis-content').classList.remove('hidden');
    }
}

function showError(msg) {
    document.getElementById('loading-skeleton').classList.add('hidden');
    document.getElementById('analysis-content').classList.remove('hidden');
    document.getElementById('ai-headline').innerText = msg;
    document.getElementById('ai-commentary').innerText = '';
}

// 2. 받아온 데이터를 HTML 요소들에 뿌려주는 함수
function updateUI(data) {

	// HTML에 만든 id="recent-headline"과 id="recent-commentary"에 매핑합니다.
	const recentHeadlineElem = document.getElementById('recent-headline');
	const recentCommentaryElem = document.getElementById('recent-commentary');

	if (recentHeadlineElem && data.recentHeadline) {
		recentHeadlineElem.innerText = data.recentHeadline;
	}
	if (recentCommentaryElem && data.recentCommentary) {
		recentCommentaryElem.innerText = data.recentCommentary;
	}
	// 최근 지출 게이지 차트 호출
	if (data.recentPercent !== undefined) {
		drawRecentGauge(data.recentPercent);
	}
	// 데이터가 없을 경우 섹션 숨기기 (예시)
	if (!data.recentHeadline) {
		document.getElementById('recent-analysis-section').classList.add('hidden');
	} else {
		document.getElementById('recent-analysis-section').classList.remove('hidden');
	}

	// 서버 응답(AnalysisResponse)의 텍스트 데이터를 HTML 요소에 삽입
	document.getElementById('ai-headline').innerText = data.aiHeadline;
	document.getElementById('ai-commentary').innerText = data.aiCommentary;
	// 데이터가 1개뿐이라면 그 항목의 퍼센트(보통 100%)를 보여주고, 아니면 기존처럼 식비 비중을 표시
	const displayPercent = (data.dataValues && data.dataValues.length === 1) 
	    ? data.dataValues[0] 
	    : (data.foodPercent || 0);

	document.getElementById('main-percent').innerText = displayPercent + "%";

	// 이상 지출(anomalies) 데이터를 리스트 형태로 렌더링
	renderAnomalies(data.anomalies);

	// 차트를 그리기 위한 라벨과 데이터 값을 전달하여 초기화
	initAnalysis(data.labels, data.dataValues);
}

// 3. 차트 초기화 및 화면 표시 전환 함수
function initAnalysis(labels, dataValues) {
    // 1. 데이터가 1개인데 0으로 왔을 경우를 대비해 100으로 보정
    let finalData = dataValues;
    if (dataValues && dataValues.length === 1 && (dataValues[0] === 0 || !dataValues[0])) {
        finalData = [100]; 
    } else {
        finalData = (dataValues && dataValues.length > 0) ? dataValues : [100];
    }

    const finalLabels = (labels && labels.length > 0) ? labels : ['데이터 없음'];

    document.getElementById('loading-skeleton').classList.add('hidden');
    document.getElementById('analysis-content').classList.remove('hidden');

    drawExpenseChart(finalLabels, finalData);
}

// 4. Chart.js 라이브러리를 이용한 도넛 차트 및 하단 카테고리 리스트 생성 함수
function drawExpenseChart(labels, data) {
    const ctx = document.getElementById('expenseChart').getContext('2d');
    const categoryListContainer = document.getElementById('category-list');
    categoryListContainer.innerHTML = '';

    // 기존 인스턴스 파괴 후 재생성
    if (expenseChartInstance) {
        expenseChartInstance.destroy();
        expenseChartInstance = null;
    }

	// label의 앞뒤 공백을 제거하고, 매핑되는 색상이 없으면 강제로 '식비'색이나 '기본'색을 할당
	const currentColors = labels.map(label => {
	    const cleanLabel = label.trim();
	    return categoryColors[cleanLabel] || categoryColors["기본"] || "#ff6b6b"; 
	});

    expenseChartInstance = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: data,
                backgroundColor: currentColors,
                borderWidth: 0,
                borderRadius: 5
            }]
        },
        options: {
            cutout: '80%',
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } }
        }
    });

	labels.forEach((label, index) => {
	    const val = data[index] || 0;
	    // 차트와 동일하게 trim() 처리된 키로 색상을 가져와야 합니다.
	    const cleanLabel = label.trim();
	    const color = categoryColors[cleanLabel] || categoryColors["기본"]; 

	    const itemHtml = `
	        <div class="flex flex-col gap-2.5">
	            <div class="flex justify-between items-end">
	                <div class="flex items-center gap-2">
	                    <div class="w-2 h-2 rounded-full" style="background-color: ${color}"></div>
	                    <span class="text-sm font-bold text-slate-700 dark:text-slate-300">${cleanLabel}</span>
	                </div>
	                <span class="text-sm font-black text-primary">${val}%</span>
	            </div>
	            <div class="w-full h-1.5 bg-slate-100 dark:bg-slate-800/50 rounded-full overflow-hidden">
	                <div class="h-full rounded-full transition-all duration-1000" style="width: ${val}%; background-color: ${color}"></div>
	            </div>
	        </div>`;
	    categoryListContainer.insertAdjacentHTML('beforeend', itemHtml);
	});
}
// 5. 이상 지출(Anomaly) 데이터를 화면에 목록 형태로 표시하는 함수
function renderAnomalies(anomalies) {
	const listContainer = document.getElementById('anomaly-list');
	listContainer.innerHTML = '';

	// 이상 지출 데이터가 없을 경우 처리
	if (!anomalies || anomalies.length === 0) {
		listContainer.innerHTML = '<p class="text-center text-slate-400 text-sm py-5">이번 달은 이상 지출이 없습니다. 😊</p>';
		return;
	}

	// 각각의 이상 지출 항목을 카드 형태로 생성
	anomalies.forEach(item => {
		const html = `
            <div class="bg-white dark:bg-slate-900 border-l-4 border-rose-500 rounded-2xl p-5 shadow-sm flex items-center justify-between mb-3 border border-slate-100 dark:border-slate-800">
                <div>
                    <p class="text-[11px] font-bold text-rose-500">주의 · ${item.date}</p>
                    <p class="text-sm font-black mt-1 dark:text-white">${item.title}</p>
                    <p class="text-xs text-slate-500 mt-0.5">${item.reason}</p>
                </div>
                <div class="text-right">
                    <p class="font-black text-rose-500">${item.amount.toLocaleString()}원</p>
                </div>
            </div>`;
		listContainer.insertAdjacentHTML('beforeend', html);
	});
}

/**
 * 최근 지출의 '강도'를 보여주는 반원 게이지 차트를 생성합니다.
 * @param {number} percent - 그룹 평균 대비 지출 비율
 */
function drawRecentGauge(percent) {
    const ctx = document.getElementById('recentGaugeChart').getContext('2d');

    // 기존 인스턴스 파괴
    if (recentGaugeInstance) {
        recentGaugeInstance.destroy();
        recentGaugeInstance = null;
    }
    if (window.myRecentGauge) {
        window.myRecentGauge.destroy();
        window.myRecentGauge = null;
    }

    const gaugeColor = percent > 120 ? '#f43f5e' : '#135bec';

    recentGaugeInstance = new Chart(ctx, {
        type: 'doughnut',
        data: {
            datasets: [{
                data: [percent, 200 - percent],
                backgroundColor: [gaugeColor, '#e2e8f0'],
                borderWidth: 0,
                circumference: 180,
                rotation: 270,
                borderRadius: 10
            }]
        },
        options: {
            cutout: '85%',
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: { enabled: false }
            }
        }
    });

    // 하위 호환 유지
    window.myRecentGauge = recentGaugeInstance;

    document.getElementById('recent-relative-percent').innerText = percent + "%";
}