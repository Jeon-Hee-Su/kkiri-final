/**
 * KKIRI 그룹 거래 내역 제어 스크립트
 */
document.addEventListener('DOMContentLoaded', () => {
    const end = new Date();
    const start = new Date();
    start.setMonth(start.getMonth() - 1);

    updateDateRangeText(formatDate(start), formatDate(end));

    const btn1m = document.getElementById('btn-1m');
    if (btn1m) setActiveStyle(btn1m);

    fetchHistory(toSqlDate(start), toSqlDate(end));
});

function applyCustomDate() {
    const startInput = document.getElementById('startDate');
    const endInput   = document.getElementById('endDate');

    if (!startInput.value || !endInput.value) {
        alert("시작일과 종료일을 모두 선택해주세요.");
        return;
    }

    clearActiveStyles();
    const customBtn = document.getElementById('custom-date-btn');
    if (customBtn) setActiveStyle(customBtn);

    updateDateRangeText(
        startInput.value.replace(/-/g, '.'),
        endInput.value.replace(/-/g, '.')
    );

    fetchHistory(startInput.value, endInput.value);
    closeDatePicker();
}

async function fetchHistory(startDate, endDate) {
    const pageElem     = document.getElementById('history-page');
    const listContainer = document.getElementById('history-list');
    const noData       = document.getElementById('no-data');

    if (!pageElem || !listContainer) return;

    const groupId = pageElem.dataset.groupId;

    listContainer.innerHTML = '<div class="p-20 text-center text-slate-400">데이터를 불러오는 중...</div>';
    noData.classList.add('hidden');

    try {
        const response = await fetch(`/api/group/${groupId}/transactions?startDate=${startDate}&endDate=${endDate}`);
        if (!response.ok) throw new Error('Fetch failed');

        const transactions = await response.json();

        if (!transactions || transactions.length === 0) {
            listContainer.innerHTML = '';
            noData.classList.remove('hidden');
            updateSummary(0, 0);
            return;
        }

        const html = transactions.map(item => {
            const isIncome = item.amount > 0;
            const isWithdraw = !isIncome;

            // 출금 거래에만 영수증 영역 표시
            let receiptArea = '';
            if (isWithdraw) {
                if (item.expenseId) {
                    // 영수증 있음
                    receiptArea = `
                        <button onclick="openReceiptDetail(event, ${item.expenseId})"
                            class="mt-1.5 flex items-center gap-1 text-[10px] text-emerald-600 font-bold hover:text-emerald-700 transition-colors">
                            <span class="material-symbols-outlined text-sm">receipt_long</span>
                            영수증 보기
                        </button>`;
                } else {
                    // 영수증 없음 → 추가 버튼
                    receiptArea = `
                        <button onclick="openAddReceipt(event, ${item.transactionId}, ${groupId}, ${Math.abs(item.amount)})"
                            class="mt-1.5 flex items-center gap-1 text-[10px] text-slate-400 font-bold hover:text-emerald-500 transition-colors">
                            <span class="material-symbols-outlined text-sm">add_circle</span>
                            영수증 추가
                        </button>`;
                }
            }

            return `
                <div class="p-5 flex items-center justify-between hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors">
                    <div class="flex items-center gap-4">
                        <div class="w-12 h-12 rounded-2xl ${isIncome ? 'bg-blue-50 text-blue-600' : 'bg-rose-50 text-rose-500'} flex items-center justify-center flex-shrink-0">
                            <span class="material-symbols-outlined">${isIncome ? 'download' : 'upload'}</span>
                        </div>
                        <div>
                            <p class="text-sm font-bold text-slate-800 dark:text-slate-100">${item.description || '내역 없음'}</p>
                            <p class="text-[10px] text-slate-400 mt-0.5">${formatDisplayDate(item.createdAt)}</p>
                            ${receiptArea}
                        </div>
                    </div>
                    <div class="text-right flex-shrink-0">
                        <p class="text-sm font-black ${isIncome ? 'text-blue-600' : 'text-slate-900 dark:text-white'}">
                            ${isIncome ? '+' : '-'}${Math.abs(item.amount).toLocaleString()}원
                        </p>
                    </div>
                </div>`;
        }).join('');

        listContainer.innerHTML = html;

        let incomeTotal = 0, expenseTotal = 0;
        transactions.forEach(item => {
            if (item.amount > 0) incomeTotal  += item.amount;
            else                  expenseTotal += Math.abs(item.amount);
        });
        updateSummary(incomeTotal, expenseTotal);

    } catch (error) {
        console.error("Error fetching history:", error);
        listContainer.innerHTML = '<div class="p-20 text-center text-rose-500 font-bold">내역을 불러오지 못했습니다.</div>';
    }
}

// =====================================================================
// 영수증 추가: 영수증 페이지로 이동 (transactionId, groupId 전달)
// =====================================================================
function openAddReceipt(event, transactionId, groupId, amount) {
    event.stopPropagation();
    window.location.href = `/receipts?transactionId=${transactionId}&groupId=${groupId}&txAmount=${amount}`;
}

// =====================================================================
// 영수증 보기: 슬라이드업 모달에서 품목 상세 표시
// =====================================================================
let currentExpenseId = null;

async function openReceiptDetail(event, expenseId) {
    event.stopPropagation();
    currentExpenseId = expenseId;  // 삭제 시 사용

    const modal    = document.getElementById('receiptDetailModal');
    const itemsEl  = document.getElementById('receipt-detail-items');
    const totalEl  = document.getElementById('receipt-detail-total');

    itemsEl.innerHTML = '<p class="text-center text-slate-400 text-sm py-6 animate-pulse">불러오는 중...</p>';
    totalEl.textContent = '';
    modal.classList.remove('hidden');
    document.body.style.overflow = 'hidden';

    try {
        const res   = await fetch(`/api/receipts/items?expenseId=${expenseId}`);
        const items = await res.json();

        if (!items || items.length === 0) {
            itemsEl.innerHTML = '<p class="text-center text-slate-400 text-sm py-6">등록된 품목이 없습니다.</p>';
            return;
        }

        let total = 0;
        itemsEl.innerHTML = items.map(item => {
            const subtotal = item.price * item.quantity;
            total += subtotal;
            return `
                <div class="flex justify-between items-center py-3 border-b border-slate-100 dark:border-slate-700 last:border-0">
                    <div>
                        <p class="font-bold text-sm text-slate-800 dark:text-slate-200">${item.itemName}</p>
                        <p class="text-[11px] text-slate-400">${item.price.toLocaleString()}원 × ${item.quantity}개</p>
                    </div>
                    <p class="font-black text-sm text-emerald-600">${subtotal.toLocaleString()}원</p>
                </div>`;
        }).join('');

        totalEl.textContent = total.toLocaleString() + '원';

    } catch (e) {
        itemsEl.innerHTML = '<p class="text-center text-red-400 text-sm py-6">품목을 불러오지 못했습니다.</p>';
    }
}

function closeReceiptDetail() {
    document.getElementById('receiptDetailModal').classList.add('hidden');
    document.body.style.overflow = 'auto';
    currentExpenseId = null;
}

async function deleteReceipt() {
    if (!currentExpenseId) return;
    if (!confirm('영수증을 삭제하시겠습니까?\n삭제된 영수증은 복구할 수 없습니다.')) return;

    const btn = document.getElementById('receipt-delete-btn');
    if (btn) { btn.disabled = true; btn.textContent = '삭제 중...'; }

    try {
        const res = await fetch(`/api/receipts/${currentExpenseId}`, {
            method: 'DELETE',
            credentials: 'include'
        });

        if (res.ok) {
            closeReceiptDetail();
            alert('영수증이 삭제되었습니다.');
            // 현재 적용된 날짜 범위로 새로고침
            const end   = new Date();
            const start = new Date();
            start.setMonth(start.getMonth() - 1);
            const startInput = document.getElementById('startDate');
            const endInput   = document.getElementById('endDate');
            const startVal = (startInput && startInput.value) ? startInput.value : toSqlDate(start);
            const endVal   = (endInput   && endInput.value)   ? endInput.value   : toSqlDate(end);
            fetchHistory(startVal, endVal);
        } else {
            alert('삭제에 실패했습니다. 다시 시도해주세요.');
            if (btn) { btn.disabled = false; btn.innerHTML = '<span class="material-symbols-outlined text-base">delete</span> 영수증 삭제'; }
        }
    } catch (e) {
        alert('서버 통신 중 오류가 발생했습니다.');
        if (btn) { btn.disabled = false; btn.innerHTML = '<span class="material-symbols-outlined text-base">delete</span> 영수증 삭제'; }
    }
}
    

// =====================================================================
// 공통 유틸리티
// =====================================================================
function changePeriod(months, btn) {
    clearActiveStyles();
    setActiveStyle(btn);
    const end   = new Date();
    const start = new Date();
    start.setMonth(start.getMonth() - months);
    updateDateRangeText(formatDate(start), formatDate(end));
    fetchHistory(toSqlDate(start), toSqlDate(end));
}

function updateDateRangeText(s, e) { document.getElementById('current-range-text').innerText = `${s} ~ ${e}`; }
function clearActiveStyles()       { document.querySelectorAll('.period-btn, #custom-date-btn').forEach(b => b.classList.remove('active', 'bg-primary', 'text-white')); }
function setActiveStyle(btn)       { if (btn) btn.classList.add('active', 'bg-primary', 'text-white'); }
function formatDate(d)             { return `${d.getFullYear()}.${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')}`; }
function toSqlDate(d)              { return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }
function formatDisplayDate(s)      { if (!s) return '-'; const d = new Date(s); return `${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`; }
function showDatePicker()          { document.getElementById('datePickerModal').classList.remove('hidden'); }
function closeDatePicker()         { document.getElementById('datePickerModal').classList.add('hidden'); }
function updateSummary(income, expense) {
    const incomeEl  = document.getElementById('total-income');
    const expenseEl = document.getElementById('total-expense');
    if (incomeEl)  incomeEl.innerText  = '+' + income.toLocaleString()  + '원';
    if (expenseEl) expenseEl.innerText = '-' + expense.toLocaleString() + '원';
}