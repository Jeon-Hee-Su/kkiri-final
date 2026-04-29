// ================================================================
// settlement.js — 정산 관리 페이지
// ================================================================

let currentGroupId      = PAGE_GROUP_ID;
let currentSettlementId = null;
let allSettlements      = [];   // 전체 캐시
let showCompleted       = false; // 완료 내역 표시 토글

// ─────────────────────────────────────────────────────────────
// 초기화
// ─────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
    await loadSettlements();
    await loadMyPending();
    await loadMembersForCreate();
});

// ─────────────────────────────────────────────────────────────
// 정산 목록 로드
// ─────────────────────────────────────────────────────────────
async function loadSettlements() {
    const list = document.getElementById('settlement-list');
    list.innerHTML = `<div class="p-10 text-center text-slate-400 text-sm">
        <span class="material-symbols-outlined animate-spin text-2xl block mx-auto mb-2">progress_activity</span>
        불러오는 중...</div>`;
    try {
        const res  = await authFetch(`/api/settlement/list?groupId=${currentGroupId}`);
        allSettlements = await res.json();
        renderList();
    } catch (e) {
        list.innerHTML = `<p class="text-center text-rose-400 py-10 text-sm">정산 목록을 불러오지 못했습니다.</p>`;
    }
}

function renderList() {
    const list = document.getElementById('settlement-list');

    // 진행중 / 완료 분리
    const proceeding = allSettlements.filter(s => s.status !== 'COMPLETED');
    const completed  = allSettlements.filter(s => s.status === 'COMPLETED');

    // 표시할 목록
    const visible = showCompleted ? allSettlements : proceeding;

    if (allSettlements.length === 0) {
        list.innerHTML = `
            <div class="bg-white dark:bg-slate-900 border border-slate-100 dark:border-slate-800 rounded-[2rem] p-10 text-center">
                <span class="material-symbols-outlined text-4xl text-slate-300 block mb-3">receipt_long</span>
                <p class="text-sm font-bold text-slate-400">정산 내역이 없습니다</p>
                <p class="text-xs text-slate-300 mt-1">정산 관리 페이지에서 알림을 보내세요</p>
            </div>`;
        return;
    }

    if (proceeding.length === 0 && !showCompleted) {
        list.innerHTML = `
            <div class="bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-100 dark:border-emerald-800 rounded-[2rem] p-8 text-center">
                <span class="material-symbols-outlined text-4xl text-emerald-400 block mb-3">check_circle</span>
                <p class="text-sm font-bold text-emerald-600 dark:text-emerald-400">진행중인 정산이 없습니다</p>
                <p class="text-xs text-emerald-500 mt-1">모든 정산이 완료되었습니다 🎉</p>
            </div>`;
    } else {
        list.innerHTML = visible.map(s => renderSettlementCard(s)).join('');
    }

    // 완료 내역 토글 버튼 (완료된 것이 있을 때만)
    if (completed.length > 0) {
        list.innerHTML += `
            <button onclick="toggleCompleted()"
                    class="w-full py-3 text-xs font-bold text-slate-400 hover:text-slate-600 transition-colors flex items-center justify-center gap-1 mt-2">
                <span class="material-symbols-outlined text-sm">${showCompleted ? 'expand_less' : 'expand_more'}</span>
                ${showCompleted ? '완료 내역 숨기기' : `완료된 정산 ${completed.length}건 보기`}
            </button>`;
    }
}

function toggleCompleted() {
    showCompleted = !showCompleted;
    renderList();
}

function renderSettlementCard(s) {
    const isCompleted = s.status === 'COMPLETED';
    const progress    = s.totalCount > 0 ? Math.round((s.paidCount / s.totalCount) * 100) : 0;
    const statusBadge = isCompleted
        ? `<span class="text-[10px] font-black px-2 py-0.5 bg-emerald-100 text-emerald-600 rounded-full">완료</span>`
        : `<span class="text-[10px] font-black px-2 py-0.5 bg-blue-100 text-blue-600 rounded-full">진행중</span>`;

    return `
        <div onclick="openDetailModal(${s.settlementId}, '${escHtml(s.title)}')"
             class="bg-white dark:bg-slate-900 border border-slate-100 dark:border-slate-800 rounded-[2rem] p-5 shadow-sm cursor-pointer active:scale-[0.98] transition-all ${isCompleted ? 'opacity-50' : ''}">
            <div class="flex justify-between items-start mb-3">
                <div>
                    <p class="font-black text-base text-slate-900 dark:text-slate-100">${escHtml(s.title)}</p>
                    <p class="text-[11px] text-slate-400 mt-0.5">${s.createdAt}</p>
                </div>
                ${statusBadge}
            </div>
            <div class="flex justify-between items-center mb-2">
                <span class="text-xs text-slate-500 font-bold">총 ${s.totalAmount.toLocaleString()}원</span>
                <span class="text-xs text-slate-400">${s.paidCount}/${s.totalCount}명 납부</span>
            </div>
            <div class="w-full h-2 bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
                <div class="h-2 rounded-full transition-all ${isCompleted ? 'bg-emerald-400' : 'bg-blue-500'}"
                     style="width: ${progress}%"></div>
            </div>
        </div>`;
}

// ─────────────────────────────────────────────────────────────
// 내 미납 정산 배너
// ─────────────────────────────────────────────────────────────
async function loadMyPending() {
    try {
        const res  = await authFetch('/api/settlement/my-pending');
        const list = await res.json();
        const banner = document.getElementById('my-pending-banner');

        if (list && list.length > 0) {
            banner.classList.remove('hidden');
            const total = list.reduce((s, d) => s + d.amountDue, 0);

            // 배너 안에 납부 항목 직접 표시
            banner.innerHTML = `
                <div class="flex items-start gap-3 mb-3">
                    <span class="material-symbols-outlined text-amber-500 text-2xl mt-0.5">warning</span>
                    <div>
                        <p class="text-sm font-black text-amber-700 dark:text-amber-400">미납 정산이 있습니다</p>
                        <p class="text-xs text-amber-600 dark:text-amber-500 mt-0.5">${list.length}건 · 합계 ${total.toLocaleString()}원</p>
                    </div>
                </div>
                <div class="flex flex-col gap-2">
                    ${list.map(d => `
                        <div class="flex items-center justify-between bg-white/60 dark:bg-slate-800/60 rounded-xl px-4 py-3">
                            <div>
                                <p class="text-sm font-bold text-slate-800 dark:text-slate-200">${escHtml(d.settlementTitle || '정산')}</p>
                                <p class="text-xs text-amber-600 font-black">${d.amountDue.toLocaleString()}원</p>
                            </div>
                            <button onclick="selfPay(${d.detailId}, ${d.settlementId}, this)"
                                    class="text-[11px] font-bold px-3 py-2 bg-amber-500 text-white rounded-xl active:scale-95 transition-all shadow-sm">
                                납부 확인
                            </button>
                        </div>`).join('')}
                </div>`;
        } else {
            banner.classList.add('hidden');
        }
    } catch (e) { /* 무시 */ }
}

// 본인 납부 직접 처리
async function selfPay(detailId, settlementId, btn) {
    btn.disabled = true;
    btn.textContent = '처리 중...';

    // 결제 비밀번호 확인 후 이체 실행
    openPaymentPasswordModal(currentGroupId, async () => {
        try {
            const res = await authFetch(`/api/settlement/pay/${detailId}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ settlementId, groupId: currentGroupId })
            });
            if (res.ok) {
                await loadMyPending();
                await loadSettlements();
            } else {
                let msg = '처리 실패. 다시 시도해주세요.';
                try {
                    const errBody = await res.json();
                    msg = errBody.message || msg;
                } catch (_) {}
                alert(msg);
                btn.disabled = false;
                btn.textContent = '납부 확인';
            }
        } catch (e) {
            alert('오류가 발생했습니다.');
            btn.disabled = false;
            btn.textContent = '납부 확인';
        }
    }, {
        title: '결제 비밀번호 확인',
        subtitle: '정산 납부를 위해 비밀번호를 입력해주세요.'
    });

    // 모달 취소 시 버튼 복구
    const origClose = window.closePaymentPasswordModal;
    window.closePaymentPasswordModal = function() {
        origClose();
        btn.disabled = false;
        btn.textContent = '납부 확인';
        window.closePaymentPasswordModal = origClose;
    };
}

// ─────────────────────────────────────────────────────────────
// 정산 상세 모달
// ─────────────────────────────────────────────────────────────
async function openDetailModal(settlementId, title) {
    currentSettlementId = settlementId;
    document.getElementById('detail-title').textContent = title;
    document.getElementById('detail-list').innerHTML =
        '<p class="text-center py-6 text-slate-400 text-sm animate-pulse">불러오는 중...</p>';
    document.getElementById('detail-modal').classList.remove('hidden');

    try {
        const res     = await authFetch(`/api/settlement/list?groupId=${currentGroupId}`);
        const allData = await res.json();
        const found   = allData.find(s => s.settlementId === settlementId);
        if (!found) return;

        const details = found.details || [];
        if (details.length === 0) {
            document.getElementById('detail-list').innerHTML =
                '<p class="text-center text-slate-400 text-sm py-6">상세 내역이 없습니다.</p>';
            return;
        }

        const isCompleted = found.status === 'COMPLETED';

        document.getElementById('detail-list').innerHTML = details.map(d => {
            const isPaid = d.status === 'PAID';
            return `
                <div class="flex items-center gap-3 py-3 border-b border-slate-100 dark:border-slate-800 last:border-0">
                    <div class="w-10 h-10 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden flex-shrink-0 flex items-center justify-center">
                        ${d.profileImg
                            ? `<img src="${d.profileImg}" class="w-full h-full object-cover" />`
                            : `<span class="material-symbols-outlined text-slate-400">person</span>`}
                    </div>
                    <div class="flex-1 min-w-0">
                        <p class="font-bold text-sm text-slate-900 dark:text-slate-100 truncate">${escHtml(d.userName)}</p>
                        <p class="text-xs font-black ${isPaid ? 'text-emerald-500' : 'text-amber-500'}">
                            ${isPaid ? '납부 완료' : '미납'} · ${d.amountDue.toLocaleString()}원
                        </p>
                    </div>
                    ${!isPaid && !isCompleted
                        ? `<button onclick="markPaid(${d.detailId}, ${settlementId}, this)"
                                class="text-[11px] font-bold px-3 py-1.5 bg-emerald-50 text-emerald-600 rounded-xl border border-emerald-200 active:scale-95 transition-all">
                            납부 확인
                           </button>`
                        : `<span class="material-symbols-outlined ${isPaid ? 'text-emerald-400' : 'text-slate-300'} text-xl">check_circle</span>`
                    }
                </div>`;
        }).join('');

    } catch (e) {
        document.getElementById('detail-list').innerHTML =
            '<p class="text-center text-rose-400 text-sm py-6">불러오기 실패</p>';
    }
}

function closeDetailModal() {
    document.getElementById('detail-modal').classList.add('hidden');
    currentSettlementId = null;
}

// ─────────────────────────────────────────────────────────────
// 납부 확인 처리
// ─────────────────────────────────────────────────────────────
async function markPaid(detailId, settlementId, btn) {
    btn.disabled = true;
    btn.textContent = '처리 중...';

    // 결제 비밀번호 확인 후 이체 실행
    openPaymentPasswordModal(currentGroupId, async () => {
        try {
            const res = await authFetch(`/api/settlement/pay/${detailId}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ settlementId, groupId: currentGroupId })
            });
            if (res.ok) {
                await loadSettlements();
                await openDetailModal(settlementId, document.getElementById('detail-title').textContent);
                await loadMyPending();
            } else {
                let msg = '처리 실패. 다시 시도해주세요.';
                try {
                    const errBody = await res.json();
                    msg = errBody.message || msg;
                } catch (_) {}
                alert(msg);
                btn.disabled = false;
                btn.textContent = '납부 확인';
            }
        } catch (e) {
            alert('오류가 발생했습니다.');
            btn.disabled = false;
            btn.textContent = '납부 확인';
        }
    }, {
        title: '결제 비밀번호 확인',
        subtitle: '정산 납부를 위해 비밀번호를 입력해주세요.'
    });

    const origClose = window.closePaymentPasswordModal;
    window.closePaymentPasswordModal = function() {
        origClose();
        btn.disabled = false;
        btn.textContent = '납부 확인';
        window.closePaymentPasswordModal = origClose;
    };
}

// ─────────────────────────────────────────────────────────────
// 정산 삭제
// ─────────────────────────────────────────────────────────────
async function deleteSettlement() {
    if (!currentSettlementId) return;
    if (!confirm('이 정산을 삭제하시겠습니까? 복구할 수 없습니다.')) return;
    try {
        const res = await authFetch(
            `/api/settlement/${currentSettlementId}?groupId=${currentGroupId}`,
            { method: 'DELETE' }
        );
        if (res.ok) {
            closeDetailModal();
            await loadSettlements();
        } else {
            alert('삭제 실패. 다시 시도해주세요.');
        }
    } catch (e) { alert('오류가 발생했습니다.'); }
}

// ─────────────────────────────────────────────────────────────
// 정산 생성 모달 (수동 생성용)
// ─────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────
// 거래 내역 조회 및 선택
// ─────────────────────────────────────────────────────────────
async function loadTxList() {
    const start   = document.getElementById('tx-start').value;
    const end     = document.getElementById('tx-end').value;
    const txList  = document.getElementById('tx-list');

    if (!start || !end) { alert('기간을 선택해주세요.'); return; }

    txList.innerHTML = '<p class="text-xs text-slate-400 text-center py-4 animate-pulse">불러오는 중...</p>';

    try {
        const res  = await authFetch(`/api/group/${currentGroupId}/transactions?startDate=${start}&endDate=${end}`);
        const data = await res.json();

        // 출금(음수 또는 QR_PAYMENT/AUTO_TRANSFER)만 필터
        const withdrawals = (data || []).filter(t =>
            t.amount < 0 ||
            t.transactionType === 'QR_PAYMENT' ||
            t.transactionType === 'AUTO_TRANSFER'
        );

        if (withdrawals.length === 0) {
            txList.innerHTML = '<p class="text-xs text-slate-400 text-center py-4">해당 기간에 출금 내역이 없습니다</p>';
            return;
        }

        txList.innerHTML = withdrawals.map(t => {
            const amt  = Math.abs(t.amount);
            const desc = t.description || t.transactionType || '거래';
            const date = t.createdAt ? String(t.createdAt).substring(0, 10) : '';
            return `
                <label class="flex items-center gap-3 p-3 bg-slate-50 dark:bg-slate-800 rounded-xl cursor-pointer
                              has-[:checked]:bg-blue-50 dark:has-[:checked]:bg-blue-900/20
                              has-[:checked]:ring-1 has-[:checked]:ring-blue-400 transition-all">
                    <input type="checkbox" class="tx-check w-4 h-4 rounded text-blue-600 flex-shrink-0"
                           value="${t.transactionId}" data-amount="${amt}" onchange="calcTxTotal()">
                    <div class="flex-1 min-w-0">
                        <p class="text-xs font-bold text-slate-800 dark:text-slate-200 truncate">${escHtml(desc)}</p>
                        <p class="text-[10px] text-slate-400">${date}</p>
                    </div>
                    <span class="text-xs font-black text-blue-600 flex-shrink-0">${amt.toLocaleString()}원</span>
                </label>`;
        }).join('');

    } catch (e) {
        txList.innerHTML = '<p class="text-xs text-rose-400 text-center py-4">불러오기 실패</p>';
    }
}

function toggleAllTx() {
    const checks = document.querySelectorAll('.tx-check');
    const allChecked = [...checks].every(c => c.checked);
    checks.forEach(c => c.checked = !allChecked);
    calcTxTotal();
}

function calcTxTotal() {
    let total = 0;
    document.querySelectorAll('.tx-check:checked').forEach(c => {
        total += parseInt(c.getAttribute('data-amount')) || 0;
    });
    document.getElementById('create-amount').value = total;
    updatePerPerson();
}

async function loadMembersForCreate() {
    try {
        const res = await authFetch(`/api/settlement/members?groupId=${currentGroupId}`);
        const members = await res.json();
        const container = document.getElementById('member-list');
        if (!container) return;
        container.innerHTML = members.map(m => `
            <label class="flex items-center gap-3 p-3 bg-slate-50 dark:bg-slate-800 rounded-xl cursor-pointer has-[:checked]:bg-blue-50 has-[:checked]:ring-1 has-[:checked]:ring-blue-400 transition-all">
                <input type="checkbox" class="member-check w-4 h-4 rounded text-blue-600"
                       value="${m.userId}" onchange="updatePerPerson()">
                <div class="w-8 h-8 rounded-full bg-slate-200 overflow-hidden flex-shrink-0 flex items-center justify-center">
                    ${m.profileImg
                        ? `<img src="${m.profileImg}" class="w-full h-full object-cover" />`
                        : `<span class="material-symbols-outlined text-slate-400 text-sm">person</span>`}
                </div>
                <span class="font-bold text-sm text-slate-800 dark:text-slate-200">${escHtml(m.userName)}</span>
            </label>`).join('');
    } catch (e) { /* 무시 */ }
}

function toggleAllMembers() {
    const checks = document.querySelectorAll('.member-check');
    const allChecked = [...checks].every(c => c.checked);
    checks.forEach(c => c.checked = !allChecked);
    updatePerPerson();
}

function updatePerPerson() {
    const amount  = parseInt(document.getElementById('create-amount').value) || 0;
    const checked = document.querySelectorAll('.member-check:checked').length;
    const preview = document.getElementById('per-person-preview');
    if (checked > 0 && amount > 0) {
        preview.classList.remove('hidden');
        document.getElementById('per-person-amount').textContent =
            Math.floor(amount / checked).toLocaleString();
    } else {
        preview.classList.add('hidden');
    }
}


function openCreateModal() {
    document.getElementById('create-modal').classList.remove('hidden');
    document.getElementById('create-title').value  = '';
    document.getElementById('create-amount').value = '';
    document.querySelectorAll('.member-check').forEach(c => c.checked = false);
    document.getElementById('per-person-preview').classList.add('hidden');
    document.getElementById('tx-list').innerHTML =
        '<p class="text-xs text-slate-400 text-center py-4">기간을 선택 후 조회하세요</p>';

    // 날짜 기본값: 최근 30일
    const now = new Date();
    const past = new Date(); past.setDate(now.getDate() - 30);
    const fmt = d => d.toISOString().split('T')[0];
    document.getElementById('tx-start').value = fmt(past);
    document.getElementById('tx-end').value   = fmt(now);
    loadTxList();
}

function closeCreateModal() {
    document.getElementById('create-modal').classList.add('hidden');
}

async function submitCreateSettlement() {
    const title  = document.getElementById('create-title').value.trim();
    const amount = parseInt(document.getElementById('create-amount').value) || 0;
    const memberIds = [...document.querySelectorAll('.member-check:checked')].map(c => parseInt(c.value));

    if (!title)           { alert('정산 제목을 입력해주세요.'); return; }
    if (amount <= 0)      { alert('금액을 입력해주세요.'); return; }
    if (memberIds.length === 0) { alert('멤버를 1명 이상 선택해주세요.'); return; }

    try {
        const res = await authFetch('/api/settlement/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ groupId: currentGroupId, title, totalAmount: amount, memberIds })
        });
        if (res.ok) {
            closeCreateModal();
            await loadSettlements();
            alert('정산이 생성되었습니다. 멤버들에게 알림이 전송됩니다.');
        } else {
            const err = await res.text();
            alert(err || '생성 실패. 다시 시도해주세요.');
        }
    } catch (e) { alert('오류가 발생했습니다.'); }
}

// ─────────────────────────────────────────────────────────────
// 유틸
// ─────────────────────────────────────────────────────────────
function escHtml(str) {
    if (!str) return '';
    return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}