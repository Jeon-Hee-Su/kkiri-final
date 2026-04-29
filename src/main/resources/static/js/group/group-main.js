// =====================================================================
// 전역 상태 변수
// =====================================================================
let currentUserId = null;
let hasMyAccount = false;
let accountList = [];
let selectedAccount = null;

// =====================================================================
// [핵심] 현재 선택된 groupId를 안전하게 읽는 공통 헬퍼
// 우선순위: group-combo 선택값 → SERVER_GROUP_ID (서버 주입값)
// =====================================================================
function getCurrentGroupId() {
	const comboGroupId = document.getElementById('group-combo')?.value;
	if (comboGroupId && !isNaN(parseInt(comboGroupId))) {
		return parseInt(comboGroupId);
	}
	if (typeof SERVER_GROUP_ID !== 'undefined' && SERVER_GROUP_ID) {
		return parseInt(SERVER_GROUP_ID);
	}
	return null;
}

// =====================================================================
// [핵심] 비밀번호 모달 공용 상태
// 'QR결제' | '회비채우기' 중 어떤 용도로 열었는지 구분
// =====================================================================
let verifyPinMode = '';
let currentVerifyPin = '';

// 회비채우기 임시 저장 (비밀번호 검증 통과 후 이체에 사용)
let pendingFillGroupId = null;
let pendingFillAmount = null;

// =====================================================================
// 유틸 함수
// =====================================================================
function getCookie(name) {
	const value = `; ${document.cookie}`;
	const parts = value.split(`; ${name}=`);
	if (parts.length === 2) return parts.pop().split(';').shift();
	return null;
}

function parseJwt(token) {
	try {
		const base64Url = token.split('.')[1];
		const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
		const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
			return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
		}).join(''));
		return JSON.parse(jsonPayload);
	} catch (e) {
		console.error("JWT 파싱 실패:", e);
		return null;
	}
}

function formatCurrency(amount) {
	return new Intl.NumberFormat('ko-KR').format(amount) + '원';
}

function escapeHtml(str) {
	return String(str)
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;');
}

// =====================================================================
// 초기 로드
// =====================================================================
document.addEventListener('DOMContentLoaded', async function() {
	console.log("1. DOM 로드 완료");

	// accessToken은 HttpOnly 쿠키라 JS에서 직접 읽을 수 없음
	// → currentUserId는 /api/user/me 에서 서버에 물어보는 방식으로 대체
	try {
		const meRes = await authFetch('/api/user/me', { credentials: 'include' });
		if (meRes.ok) {
			const me = await meRes.json();
			currentUserId = me.userId;
			console.log("currentUserId 할당 성공:", currentUserId);
		}
	} catch (e) {
		console.warn("유저 정보 조회 실패:", e);
	}

	// SERVER_GROUP_ID가 있으면 userId 없이도 그룹 데이터 바로 로드
	if (typeof SERVER_GROUP_ID !== 'undefined' && SERVER_GROUP_ID && SERVER_GROUP_ID !== 0) {
		fetchAccountData();
		await fetchGroupDetail(SERVER_GROUP_ID);
		await populateGroupCombo(currentUserId);
	} else if (currentUserId) {
		fetchAccountData();
		await initGroupData(currentUserId);
	}

	const accountForm = document.querySelector('#accountModal form');
	if (accountForm) {
		accountForm.addEventListener('submit', handleAccountSubmit);
	}

	const groupSelect = document.getElementById('group-select');
	if (groupSelect) {
		groupSelect.addEventListener('change', function(e) {
			const selectedOption = e.target.options[e.target.selectedIndex];
			const fee = selectedOption.dataset.fee;
			if (fee && fee > 0) {
				document.getElementById('fill-amount').value = fee;
			}
		});
	}
});

// =====================================================================
// 그룹 콤보박스만 채우기 (상세조회는 별도로 이미 한 경우)
// =====================================================================
async function populateGroupCombo(userId) {
	const groupCombo = document.getElementById('group-combo');
	if (!groupCombo || !userId) return;

	try {
		const response = await authFetch(`/api/group/list?userId=${userId}`, {
			method: 'GET', credentials: 'include'
		});
		if (response.ok) {
			const groups = await response.json();
			groupCombo.innerHTML = '';
			groups.forEach(group => {
				const option = document.createElement('option');
				option.value = group.groupId;
				option.text = group.groupName;
				if (group.groupId === parseInt(SERVER_GROUP_ID)) option.selected = true;
				groupCombo.appendChild(option);
			});
		}
	} catch (e) {
		console.error("그룹 콤보 로드 실패:", e);
	}
}

// =====================================================================
// 그룹 데이터 초기화 및 상세 조회
// =====================================================================
async function initGroupData(userId) {
	const groupCombo = document.getElementById('group-combo');
	if (!userId) return;

	// ✅ 서버가 Thymeleaf로 주입한 groupId를 우선 사용
	// group-main.html의 <script th:inline="javascript"> 블록에서 SERVER_GROUP_ID가 주입됨
	const targetGroupId = (typeof SERVER_GROUP_ID !== 'undefined' && SERVER_GROUP_ID)
		? parseInt(SERVER_GROUP_ID)
		: null;
	console.log("서버에서 받은 targetGroupId:", targetGroupId);

	try {
		const response = await authFetch(`/api/group/list?userId=${userId}`, {
			method: 'GET',
			credentials: 'include'
		});

		if (response.ok) {
			const groups = await response.json();

			if (groupCombo && groups.length > 0) {
				groupCombo.innerHTML = '';
				groups.forEach((group) => {
					const option = document.createElement('option');
					option.value = group.groupId;
					option.text = group.groupName;
					// ✅ URL의 groupId와 일치하는 항목을 selected, 없으면 첫 번째 선택
					if (targetGroupId ? group.groupId === targetGroupId : false) {
						option.selected = true;
					}
					groupCombo.appendChild(option);
				});

				// ✅ URL groupId가 있으면 그걸 우선 조회, 없으면 첫 번째 그룹 조회
				const firstGroupId = targetGroupId || groups[0].groupId;
				// combo에서 해당 그룹이 없을 경우 fallback
				const matched = groups.find(g => g.groupId === firstGroupId);
				const loadGroupId = matched ? firstGroupId : groups[0].groupId;

				if (!matched && targetGroupId) {
					console.warn("URL의 groupId가 내 그룹 목록에 없어 첫 번째 그룹으로 대체합니다.");
				}

				// combo 선택 상태 보정
				groupCombo.value = loadGroupId;
				await fetchGroupDetail(loadGroupId);

			} else if (groupCombo) {
				groupCombo.innerHTML = '<option value="">소속된 그룹 없음</option>';
			}
		}
	} catch (error) {
		console.error("그룹 목록 로드 에러:", error);
	}
}

async function fetchGroupDetail(groupId) {
	try {
		const response = await authFetch(`/api/group/detail/${groupId}`, {
			credentials: 'include'
		});

		if (response.ok) {
			const detail = await response.json();

			const nameDisplay = document.getElementById('display-group-name');
			if (nameDisplay) nameDisplay.textContent = detail.groupName;

			const accInfo = document.getElementById('display-group-account-number');
			if (accInfo) accInfo.textContent = `${detail.bankName || '모임통장'} (${detail.accountNumber})`;

			const balanceText = document.getElementById('group-account-balance-text');
			if (balanceText) balanceText.textContent = formatCurrency(detail.balance || 0);

			const memberLink = document.getElementById('member-manage-link');
			if (memberLink) memberLink.href = `/groupmembers?groupId=${groupId}`;

			await fetchGroupTransactions(groupId);
		}
	} catch (error) {
		console.error("그룹 상세 조회 실패:", error);
	}
}

function handleGroupSwitch(groupId) {
    location.href = `/group/detail/${groupId}`;
}

// =====================================================================
// 거래내역 조회 및 렌더링
// =====================================================================
async function fetchGroupTransactions(groupId, limit = 10) {
	const container = document.getElementById('transaction-list');
	if (!container) return;

	container.innerHTML = `
		<div class="p-6 text-center text-slate-400 text-sm">
			<span class="material-symbols-outlined animate-spin text-2xl">progress_activity</span>
			<p class="mt-2">거래 내역을 불러오는 중...</p>
		</div>`;

	try {
		const response = await authFetch(`/api/group/${groupId}/transactions?limit=${limit}`, {
			credentials: 'include'
		});

		if (!response.ok) throw new Error('거래내역 API 오류: ' + response.status);

		const transactions = await response.json();
		renderTransactions(container, transactions);
	} catch (error) {
		console.error("거래내역 조회 실패:", error);
		container.innerHTML = `
            <div class="p-6 text-center text-slate-400 text-sm">
                <span class="material-symbols-outlined text-2xl">error_outline</span>
                <p class="mt-2">거래 내역을 불러올 수 없습니다.</p>
            </div>`;
	}
}

function renderTransactions(container, transactions) {
	if (!transactions || transactions.length === 0) {
		container.innerHTML = `
            <div class="p-8 text-center text-slate-400 text-sm">
                <span class="material-symbols-outlined text-3xl mb-2 block">receipt_long</span>
                아직 거래 내역이 없습니다.
            </div>`;
		return;
	}
	container.innerHTML = transactions.map(tx => buildTransactionItem(tx)).join('');
}

function buildTransactionItem(tx) {
	const type = tx.transactionType;
	const isDeposit = type === 'V_ACCOUNT_DEPOSIT' || type === 'TRANSFER';
	const isQr = type === 'QR_PAYMENT';
	const isPayment = type === 'PAYMENT';

	let iconConfig, amountText, typeLabel;

	if (isDeposit) {
		iconConfig = { bg: 'bg-blue-50 dark:bg-blue-900/30', color: 'text-blue-600', icon: 'download' };
		amountText = `<p class="font-bold text-sm text-blue-500">+${formatCurrency(tx.amount)}</p>`;
		typeLabel = type === 'V_ACCOUNT_DEPOSIT' ? '포트원 입금' : '회비 입금';
	} else if (isQr) {
		iconConfig = { bg: 'bg-amber-50 dark:bg-amber-900/30', color: 'text-amber-600', icon: 'qr_code_2' };
		amountText = `<p class="font-bold text-sm text-red-500">-${formatCurrency(tx.amount)}</p>`;
		typeLabel = '현장결제';
	} else {
		iconConfig = { bg: 'bg-slate-100 dark:bg-slate-800', color: 'text-slate-600', icon: 'payments' };
		amountText = `<p class="font-bold text-sm text-red-500">-${formatCurrency(tx.amount)}</p>`;
		typeLabel = '결제';
	}

	const dateStr = formatTxDate(tx.createdAt);

	// 영수증이 이미 연결된 경우: 영수증 보기 버튼
	// 영수증이 없고 QR결제인 경우: 영수증 추가 버튼
	let receiptBtn = '';
	if (tx.expenseId) {
		receiptBtn = `<button onclick="openReceiptModal(${tx.expenseId}, '${escapeHtml(tx.description || typeLabel)}')"
			class="mt-1 flex items-center gap-0.5 text-[10px] text-emerald-600 hover:text-emerald-700 font-bold transition-colors">
			<span class="material-symbols-outlined text-sm">receipt_long</span>
			영수증 보기
		</button>`;
	} else if (isQr) {
		receiptBtn = `<button onclick="goToReceiptPage(${tx.transactionId}, ${getCurrentGroupId()})"
			class="mt-1 flex items-center gap-0.5 text-[10px] text-slate-400 hover:text-emerald-600 font-bold transition-colors">
			<span class="material-symbols-outlined text-sm">add_circle</span>
			영수증 추가
		</button>`;
	}

	return `
        <div class="p-5 flex items-center justify-between active:bg-slate-50 dark:active:bg-slate-800/50 transition-colors">
            <div class="flex items-center gap-4">
                <div class="w-11 h-11 rounded-full ${iconConfig.bg} flex items-center justify-center flex-shrink-0">
                    <span class="material-symbols-outlined ${iconConfig.color} text-xl">${iconConfig.icon}</span>
                </div>
                <div class="min-w-0">
                    <p class="font-bold text-sm truncate">${escapeHtml(tx.description || typeLabel)}</p>
                    <p class="text-[11px] text-slate-500 mt-0.5">${dateStr} · ${typeLabel}</p>
                    ${receiptBtn}
                </div>
            </div>
            <div class="text-right flex-shrink-0">${amountText}</div>
        </div>`;
}

// =====================================================================
// 영수증 상세 모달
// =====================================================================
function goToReceiptPage(transactionId, groupId) {
	window.location.href = `/receipts?transactionId=${transactionId}&groupId=${groupId}`;
}

// =====================================================================
// 영수증 등록용 거래 선택 모달
// =====================================================================
async function openReceiptSelectModal() {
	const modal = document.getElementById('receiptSelectModal');
	const list  = document.getElementById('receipt-select-list');
	if (!modal) return;

	list.innerHTML = '<p class="text-center text-slate-400 text-sm py-6 animate-pulse">불러오는 중...</p>';
	modal.classList.remove('hidden');
	document.body.style.overflow = 'hidden';

	const groupId = getCurrentGroupId();
	if (!groupId) {
		list.innerHTML = '<p class="text-center text-slate-400 text-sm py-6">그룹을 먼저 선택해주세요.</p>';
		return;
	}

	try {
		// 최근 30건 조회 후 출금 + 영수증 없는 것만 필터
		const res = await authFetch(`/api/group/${groupId}/transactions?limit=30`, { credentials: 'include' });
		if (!res.ok) throw new Error();
		const transactions = await res.json();

		const withdrawals = transactions.filter(tx =>
			tx.transactionType === 'QR_PAYMENT' && !tx.expenseId
		);

		if (!withdrawals || withdrawals.length === 0) {
			list.innerHTML = '<p class="text-center text-slate-400 text-sm py-6">영수증을 추가할 수 있는<br>출금 거래가 없습니다.</p>';
			return;
		}

		list.innerHTML = withdrawals.map(tx => `
			<div onclick="selectTransactionForReceipt(${tx.transactionId}, ${groupId})"
				class="flex items-center justify-between py-3.5 cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800/50 rounded-xl px-2 transition-colors">
				<div class="flex items-center gap-3">
					<div class="w-9 h-9 rounded-full bg-rose-50 dark:bg-rose-900/30 flex items-center justify-center flex-shrink-0">
						<span class="material-symbols-outlined text-rose-500 text-lg">upload</span>
					</div>
					<div>
						<p class="font-bold text-sm text-slate-800 dark:text-slate-100">${escapeHtml(tx.description || '현장결제')}</p>
						<p class="text-[10px] text-slate-400 mt-0.5">${formatTxDate(tx.createdAt)}</p>
					</div>
				</div>
				<div class="text-right flex-shrink-0">
					<p class="font-black text-sm text-rose-500">-${formatCurrency(tx.amount)}</p>
					<p class="text-[10px] text-emerald-600 font-bold mt-0.5">영수증 추가 →</p>
				</div>
			</div>`
		).join('');

	} catch (e) {
		list.innerHTML = '<p class="text-center text-red-400 text-sm py-6">거래내역을 불러오지 못했습니다.</p>';
	}
}

function closeReceiptSelectModal() {
	const modal = document.getElementById('receiptSelectModal');
	if (modal) modal.classList.add('hidden');
	document.body.style.overflow = 'auto';
}

function selectTransactionForReceipt(transactionId, groupId) {
	closeReceiptSelectModal();
	window.location.href = `/receipts?transactionId=${transactionId}&groupId=${groupId}`;
}

async function openReceiptModal(expenseId, title) {
	const modal = document.getElementById('receiptModal');
	if (!modal) return;

	document.getElementById('receipt-modal-title').textContent = title;
	document.getElementById('receipt-modal-items').innerHTML =
		'<p class="text-center text-slate-400 text-sm py-6 animate-pulse">불러오는 중...</p>';
	document.getElementById('receipt-modal-total').textContent = '';

	modal.classList.remove('hidden');
	document.body.style.overflow = 'hidden';

	try {
		const res = await authFetch(`/api/receipts/items?expenseId=${expenseId}`);
		const items = await res.json();

		if (!items || items.length === 0) {
			document.getElementById('receipt-modal-items').innerHTML =
				'<p class="text-center text-slate-400 text-sm py-6">등록된 품목이 없습니다.</p>';
			return;
		}

		let total = 0;
		document.getElementById('receipt-modal-items').innerHTML = items.map(item => {
			const subtotal = item.price * item.quantity;
			total += subtotal;
			return `
				<div class="flex justify-between items-center py-3 border-b border-slate-100 dark:border-slate-700 last:border-0">
					<div>
						<p class="font-bold text-sm text-slate-800 dark:text-slate-200">${escapeHtml(item.itemName)}</p>
						<p class="text-[11px] text-slate-400">${item.price.toLocaleString()}원 × ${item.quantity}개</p>
					</div>
					<p class="font-black text-sm text-emerald-600">${subtotal.toLocaleString()}원</p>
				</div>`;
		}).join('');

		document.getElementById('receipt-modal-total').textContent = formatCurrency(total);

	} catch (e) {
		document.getElementById('receipt-modal-items').innerHTML =
			'<p class="text-center text-red-400 text-sm py-6">품목을 불러오지 못했습니다.</p>';
	}
}

function closeReceiptModal() {
	const modal = document.getElementById('receiptModal');
	if (modal) modal.classList.add('hidden');
	document.body.style.overflow = 'auto';
}


function formatTxDate(createdAt) {
	if (!createdAt) return '';
	try {
		const d = new Date(createdAt);
		const mm = String(d.getMonth() + 1).padStart(2, '0');
		const dd = String(d.getDate()).padStart(2, '0');
		const hh = String(d.getHours()).padStart(2, '0');
		const min = String(d.getMinutes()).padStart(2, '0');
		return `${mm}.${dd} ${hh}:${min}`;
	} catch (e) {
		return '';
	}
}

// =====================================================================
// 개인 계좌 조회 및 UI
// =====================================================================
async function fetchAccountData() {
	try {
		const groupId = getCurrentGroupId();

		// groupId가 있으면 그룹에 연결된 계좌 우선 조회
		const url = groupId
			? `/api/group/${groupId}/my-linked-account`
			: '/api/account/my-account';

		const response = await authFetch(url, { credentials: 'include' });
		if (response.ok) {
			const data = await response.json();
			if (data.hasAccount && data.account && data.account.accountNumber) {
				updateAccountUI([data.account]);
				hasMyAccount = true;
				selectedAccount = data.account;
				return;
			}
		}

		// 연결 계좌가 없을 경우 주계좌(fallback) 조회
		if (groupId) {
			const fallback = await authFetch('/api/account/my-account', { credentials: 'include' });
			if (fallback.ok) {
				const fd = await fallback.json();
				if (fd.hasAccount && fd.account && fd.account.accountNumber) {
					updateAccountUI([fd.account]);
					hasMyAccount = true;
					selectedAccount = fd.account;
					return;
				}
			}
		}

		hasMyAccount = false;
	} catch (error) {
		console.error("잔액 조회 실패:", error);
	}
}

function updateAccountUI(accounts) {
	accountList = accounts;
	const privateAccInfo = document.getElementById('account-info-text');
	if (privateAccInfo && accounts.length > 0) {
		privateAccInfo.textContent = `${accounts[0].bankName || '내 계좌'} (${accounts[0].accountNumber})`;
	}
	const personalBalance = document.getElementById('account-balance-text');
	if (personalBalance && accounts.length > 0) {
		personalBalance.textContent = formatCurrency(accounts[0].balance || 0);
	}
}

async function handleAccountSubmit(e) {
	e.preventDefault();
	const bankSelect = e.target.querySelector('select');
	const accountNumberInput = e.target.querySelector('input[type="number"]');
	const bankCode = bankSelect.value;
	const accountNumber = accountNumberInput.value;

	if (!accountNumber) {
		alert("계좌번호를 입력해주세요.");
		return;
	}

	try {
		const response = await authFetch('/api/account/register', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			credentials: 'include',
			body: JSON.stringify({ bankCode, accountNumber })
		});

		if (response.ok) {
			alert("계좌가 성공적으로 등록되었습니다!\n테스트 지원금 1,000,000원이 입금되었습니다.");
			hasMyAccount = true;
			closeAccountModal();
			location.reload();
		} else if (response.status === 401) {
			alert("로그인 세션이 만료되었습니다. 다시 로그인해주세요.");
			window.location.href = "/login";
		} else {
			const errorMsg = await response.text();
			alert("등록 실패: " + (errorMsg || "서버 오류가 발생했습니다."));
		}
	} catch (error) {
		console.error("API 통신 에러:", error);
		alert("서버와 통신 중 오류가 발생했습니다.");
	}
}

// =====================================================================
// 회비 채우기
// =====================================================================
async function handleFillMoney() {
	const groupId = getCurrentGroupId();

	if (!groupId) {
		alert('선택된 그룹이 없습니다.');
		return;
	}

	const groupName = document.getElementById('display-group-name')?.textContent || '현재 그룹';
	const groupSelect = document.getElementById('group-select');
	if (groupSelect) {
		groupSelect.innerHTML = `<option value="${groupId}" selected>${groupName}</option>`;
		const combo = document.getElementById('group-combo');
		const selected = combo?.options[combo?.selectedIndex];
		const fee = selected?.dataset?.fee;
		if (fee && fee > 0) {
			document.getElementById('fill-amount').value = fee;
		}
	}

	const fillGroupName = document.getElementById('fill-group-name');
	if (fillGroupName) fillGroupName.textContent = groupName;

	openFillMoneyModal();

	if (!hasMyAccount) {
		setTimeout(() => {
			if (confirm("내 계좌가 등록되어 있지 않습니다. 계좌 등록 화면으로 이동할까요?")) {
				closeFillMoneyModal();
				window.location.href = '/paymentmethod';
			}
		}, 300);
	}
}

async function fetchUserGroupsForModal() {
	const groupSelect = document.getElementById('group-select');
	if (!groupSelect) return;

	try {
		const response = await authFetch('/api/group/my-groups', { credentials: 'include' });
		if (response.ok) {
			const groups = await response.json();
			if (groups.length > 0) {
				groupSelect.innerHTML = '<option value="" disabled selected>모임을 선택해 주세요</option>';
				groups.forEach(group => {
					const option = document.createElement('option');
					option.value = group.groupId;
					option.text = group.groupName;
					option.dataset.fee = group.dueAmount || 0;
					groupSelect.appendChild(option);
				});
			} else {
				groupSelect.innerHTML = '<option value="" disabled selected>가입된 모임이 없습니다.</option>';
			}
		}
	} catch (error) {
		console.error("그룹 목록 조회 실패:", error);
	}
}

// 회비채우기 확인 버튼 → 비밀번호 모달 오픈
function submitFillMoney() {
	const groupSelect = document.getElementById('group-select');
	const groupId = groupSelect.value;
	const amount = document.getElementById('fill-amount').value;

	if (!groupId || !amount || amount <= 0) {
		alert("입금할 모임과 금액을 정확히 입력해주세요.");
		return;
	}
	if (!hasMyAccount || !selectedAccount) {
		alert("내 계좌가 등록되어 있지 않습니다. 계좌를 먼저 등록해주세요.");
		closeFillMoneyModal();
		window.location.href = '/paymentmethod';
		return;
	}

	// 이체 정보 임시 저장
	pendingFillGroupId = parseInt(groupId);
	pendingFillAmount = parseInt(amount);

	// 비밀번호 모달을 '회비채우기' 모드로 오픈
	openVerifyPasswordModal('회비채우기');
}

// 실제 이체 실행 (비밀번호 검증 통과 후 호출됨)
async function executeTransfer() {
	try {
		const response = await authFetch('/api/group/deposit', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			credentials: 'include',
			body: JSON.stringify({
				groupId: pendingFillGroupId,
				amount: pendingFillAmount,
				accountNumber: selectedAccount.accountNumber
			})
		});

		const result = await response.json();

		if (response.ok) {
			closeVerifyPasswordModal();
			closeFillMoneyModal();
			alert(`🎉 성공! ${new Intl.NumberFormat('ko-KR').format(pendingFillAmount)}원이 모임 통장으로 입금되었습니다.`);
			fetchAccountData();
			location.reload();
		} else {
			alert("입금 실패: " + (result.error || "알 수 없는 오류가 발생했습니다."));
			closeVerifyPasswordModal();
		}
	} catch (error) {
		console.error("입금 API 통신 에러:", error);
		alert("서버와 통신 중 오류가 발생했습니다.");
		closeVerifyPasswordModal();
	} finally {
		pendingFillGroupId = null;
		pendingFillAmount = null;
	}
}

// =====================================================================
// QR 현장결제
// =====================================================================
let qrCountdownInterval = null;
let qrSelectedGroupId = null;

async function openPaymentModal() {
	document.getElementById('paymentModal').classList.remove('hidden');
	document.body.style.overflow = 'hidden';

	const groupId = getCurrentGroupId();

	if (!groupId) {
		document.getElementById('qr-hint').textContent = '선택된 그룹이 없습니다.';
		return;
	}

	qrSelectedGroupId = groupId;
	document.getElementById('qr-hint').classList.add('hidden');
	document.getElementById('qr-balance-box').classList.remove('hidden');

	resetQrArea();
	await generateQrCode();
}

function closePaymentModal() {
	document.getElementById('paymentModal').classList.add('hidden');
	document.body.style.overflow = 'auto';
	clearInterval(qrCountdownInterval);
	qrCountdownInterval = null;
	qrSelectedGroupId = null;
	document.getElementById('qr-code-container').innerHTML = '';
	document.getElementById('qr-code-container').classList.add('hidden');
	document.getElementById('qr-loading').classList.remove('hidden');
	document.getElementById('qr-timer').classList.add('hidden');
	document.getElementById('qr-expired-msg').classList.add('hidden');
	document.getElementById('qr-hint').classList.remove('hidden');
	document.getElementById('qr-balance-box').classList.add('hidden');
	// 결제 완료 메시지 제거
	const completeMsg = document.getElementById('qr-complete-msg');
	if (completeMsg) completeMsg.remove();
}

async function loadGroupsForQr() {
	try {
		const res = await authFetch('/api/payment/qr/groups', { method: 'POST', credentials: 'include' });
		if (!res.ok) throw new Error('그룹 목록 로드 실패');
		const groups = await res.json();

		const sel = document.getElementById('qr-group-select');
		if (!groups || groups.length === 0) {
			sel.innerHTML = '<option value="">소속 그룹 없음</option>';
			return;
		}

		sel.innerHTML = '<option value="">그룹을 선택해주세요</option>' +
			groups.map(g =>
				`<option value="${g.groupId}" data-balance="${g.balance}">
                    ${g.groupName} (잔액: ${Number(g.balance).toLocaleString()}원)
                </option>`
			).join('');

		const autoGroupId = getCurrentGroupId();
		if (autoGroupId) {
			sel.value = String(autoGroupId);
			onQrGroupChange(autoGroupId);
		}
	} catch (e) {
		console.error('그룹 목록 로드 실패:', e);
	}
}

function onQrGroupChange(groupId) {
	if (!groupId) return;
	qrSelectedGroupId = parseInt(groupId);

	const sel = document.getElementById('qr-group-select');
	const selected = sel.options[sel.selectedIndex];
	const balance = selected ? selected.dataset.balance : 0;
	document.getElementById('qr-balance-text').textContent = Number(balance).toLocaleString() + '원';
	document.getElementById('qr-balance-box').classList.remove('hidden');
	document.getElementById('qr-hint').classList.add('hidden');

	clearInterval(qrCountdownInterval);
	resetQrArea();
	generateQrCode();
}

function resetQrArea() {
	const completeMsg = document.getElementById('qr-complete-msg');
	if (completeMsg) completeMsg.remove();
	document.getElementById('qr-code-container').innerHTML = '';
	document.getElementById('qr-code-container').classList.add('hidden');
	document.getElementById('qr-code-container').style.display = '';
	document.getElementById('qr-loading').classList.remove('hidden');
	document.getElementById('qr-loading').style.display = '';
	document.getElementById('qr-timer').classList.add('hidden');
	document.getElementById('qr-expired-msg').classList.add('hidden');
}

function regenerateQr() {
	if (!qrSelectedGroupId) return;
	clearInterval(qrCountdownInterval);
	// 결제 완료 메시지 제거
	const completeMsg = document.getElementById('qr-complete-msg');
	if (completeMsg) completeMsg.remove();
	resetQrArea();
	generateQrCode();
}

async function generateQrCode() {
	if (!qrSelectedGroupId) return;

	try {
		const res = await authFetch('/api/payment/qr/generate', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			credentials: 'include',
			body: JSON.stringify({ groupId: qrSelectedGroupId })
		});

		if (!res.ok) {
			alert('QR 생성 실패: ' + await res.text());
			return;
		}

		const data = await res.json();
		document.getElementById('qr-balance-text').textContent = Number(data.balance).toLocaleString() + '원';

		const container = document.getElementById('qr-code-container');
		container.innerHTML = '';
		new QRCode(container, {
			text: data.token,
			width: 168,
			height: 168,
			colorDark: '#1e293b',
			colorLight: '#ffffff',
			correctLevel: QRCode.CorrectLevel.M
		});

		document.getElementById('qr-loading').classList.add('hidden');
		container.classList.remove('hidden');
		document.getElementById('qr-timer').classList.remove('hidden');

		let remaining = data.expiresIn || 180;
		updateQrCountdown(remaining);
		qrCountdownInterval = setInterval(() => {
			remaining--;
			updateQrCountdown(remaining);
			if (remaining <= 0) {
				clearInterval(qrCountdownInterval);
				container.classList.add('hidden');
				document.getElementById('qr-timer').classList.add('hidden');
				document.getElementById('qr-expired-msg').classList.remove('hidden');
			}
		}, 1000);
	} catch (e) {
		console.error('QR 생성 오류:', e);
		alert('QR 코드 생성 중 오류가 발생했습니다.');
	}
}

function updateQrCountdown(seconds) {
	const min = Math.floor(seconds / 60);
	const sec = seconds % 60;
	const el = document.getElementById('qr-countdown');
	el.textContent = `${min}:${String(sec).padStart(2, '0')}`;
	if (seconds <= 30) {
		el.classList.remove('text-primary');
		el.classList.add('text-red-500');
	} else {
		el.classList.add('text-primary');
		el.classList.remove('text-red-500');
	}
}

// =====================================================================
// 모달 열기 / 닫기
// =====================================================================
function openFillMoneyModal() {
	document.getElementById('fillMoneyModal').classList.remove('hidden');
	document.body.style.overflow = 'hidden';
}

function closeFillMoneyModal() {
	document.getElementById('fillMoneyModal').classList.add('hidden');
	document.body.style.overflow = 'auto';
}

function openAccountModal() {
	const modal = document.getElementById('accountModal');
	if (modal) modal.classList.remove('hidden');
	document.body.style.overflow = 'hidden';
}

function closeAccountModal() {
	const modal = document.getElementById('accountModal');
	if (modal) modal.classList.add('hidden');
	document.body.style.overflow = 'auto';
}

window.onclick = function(event) {
	const accModal = document.getElementById('accountModal');
	const payModal = document.getElementById('paymentModal');
	const fillModal = document.getElementById('fillMoneyModal');
	const receiptModal = document.getElementById('receiptModal');
	const receiptSelectModal = document.getElementById('receiptSelectModal');
	if (event.target === accModal) closeAccountModal();
	if (event.target === payModal) closePaymentModal();
	if (event.target === fillModal) closeFillMoneyModal();
	if (event.target === receiptModal) closeReceiptModal();
	if (event.target === receiptSelectModal) closeReceiptSelectModal();
};

// =====================================================================
// [공용] 2차 비밀번호 확인 모달
// mode: 'QR결제' | '회비채우기'
// =====================================================================

// 모달 오픈 (용도에 따라 안내 문구 다르게)
function openVerifyPasswordModal(mode) {
	verifyPinMode = mode;
	currentVerifyPin = '';
	updateVerifyDots();

	const statusEl = document.getElementById('verify-pin-status-text');
	if (statusEl) {
		if (mode === '회비채우기') {
			statusEl.textContent = '출금을 위해 2차 비밀번호를 입력해주세요.';
		} else {
			statusEl.textContent = '안전한 결제를 위해 2차 비밀번호를 입력해주세요.';
		}
		statusEl.classList.remove('text-red-500');
		statusEl.classList.add('text-slate-500');
	}

	document.getElementById('verifyPasswordModal').classList.remove('hidden');
	document.body.style.overflow = 'hidden';
}

// 기존 QR결제 버튼에서 호출하던 함수 (하위 호환 유지)
function openPasswordModal() {
	openVerifyPasswordModal('QR결제');
}

function closeVerifyPasswordModal() {
	document.getElementById('verifyPasswordModal').classList.add('hidden');
	document.body.style.overflow = 'auto';
	currentVerifyPin = '';
	verifyPinMode = '';
}

// 키패드 입력
function verifyPinInput(num) {
	if (currentVerifyPin.length < 6) {
		currentVerifyPin += num;
		updateVerifyDots();
		if (currentVerifyPin.length === 6) {
			executePasswordVerification();
		}
	}
}

// 백스페이스
function verifyPinDelete() {
	if (currentVerifyPin.length > 0) {
		currentVerifyPin = currentVerifyPin.slice(0, -1);
		updateVerifyDots();
	}
}

// 점(Dot) 업데이트
function updateVerifyDots() {
	const dots = document.querySelectorAll('.verify-pin-dot');
	dots.forEach((dot, index) => {
		if (index < currentVerifyPin.length) {
			dot.classList.replace('border-slate-200', 'border-primary');
			dot.classList.add('bg-primary');
		} else {
			dot.classList.replace('border-primary', 'border-slate-200');
			dot.classList.remove('bg-primary');
		}
	});
}

// =====================================================================
// [핵심] 비밀번호 검증 → 모드에 따라 분기
// =====================================================================
async function executePasswordVerification() {
	const statusText = document.getElementById('verify-pin-status-text');
	statusText.textContent = '확인 중...';
	statusText.classList.remove('text-red-500');
	statusText.classList.add('text-slate-500');

	try {
		let apiUrl, requestBody;

		if (verifyPinMode === '회비채우기') {
			// 개인 계좌 2차 비밀번호 검증 (userId는 서버에서 JWT로 추출)
			apiUrl = '/api/account/verify-my-password';
			requestBody = {
				paymentPassword: currentVerifyPin
			};
		} else {
			// QR결제: 그룹 계좌 2차 비밀번호 검증
			apiUrl = '/api/account/verify-password';

			const groupId = getCurrentGroupId();

			if (!groupId) {
				statusText.textContent = '그룹 정보를 찾을 수 없습니다.';
				statusText.classList.replace('text-slate-500', 'text-red-500');
				currentVerifyPin = '';
				setTimeout(() => updateVerifyDots(), 300);
				return;
			}

			requestBody = {
				groupId: groupId,
				paymentPassword: currentVerifyPin
			};
		}

		const response = await authFetch(apiUrl, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			credentials: 'include',
			body: JSON.stringify(requestBody)
		});

		const result = await response.json();

		if (result.success) {
			// ✅ 검증 성공 → 모드에 따라 다음 동작 분기
			if (verifyPinMode === '회비채우기') {
				await executeTransfer();
			} else {
				closeVerifyPasswordModal();
				await openPaymentModal();
			}
		} else {
			// 실패 처리
			if (result.errorCode === 'INACTIVE_GROUP') {
				statusText.textContent = '활동중인 계좌가 아닙니다.';
			} else {
				statusText.textContent = '비밀번호가 일치하지 않습니다. 다시 입력해주세요.';
			}
			statusText.classList.remove('text-slate-500');
			statusText.classList.add('text-red-500');
			currentVerifyPin = '';
			setTimeout(() => updateVerifyDots(), 300);
		}

	} catch (error) {
		console.error('검증 에러:', error);
		statusText.textContent = '서버 통신 오류가 발생했습니다.';
		statusText.classList.remove('text-slate-500');
		statusText.classList.add('text-red-500');
		currentVerifyPin = '';
		updateVerifyDots();
	}
}