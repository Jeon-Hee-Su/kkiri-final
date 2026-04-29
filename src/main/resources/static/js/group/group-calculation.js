document.addEventListener('DOMContentLoaded', function() {
    const startDateInput = document.getElementById('start-date');
    const endDateInput = document.getElementById('end-date');
    const startDateDisplay = document.getElementById('start-date-display');
    const endDateDisplay = document.getElementById('end-date-display');
    
    const mainContainer = document.getElementById('main-container');
    const groupId = mainContainer ? mainContainer.dataset.groupId : null;
    const finalAmountDisplay = document.getElementById('final-amount');

    // --- [1] 초기 날짜 설정 로직 (종료:오늘, 시작:일주일전) ---
    function initDefaultDates() {
        const now = new Date();
        const oneWeekAgo = new Date();
        oneWeekAgo.setDate(now.getDate() - 7);

        const formatDate = (date) => {
            const y = date.getFullYear();
            const m = String(date.getMonth() + 1).padStart(2, '0');
            const d = String(date.getDate()).padStart(2, '0');
            return `${y}-${m}-${d}`;
        };

        const todayStr = formatDate(now);
        const lastWeekStr = formatDate(oneWeekAgo);

        if (startDateInput && endDateInput) {
            startDateInput.value = lastWeekStr;
            endDateInput.value = todayStr;
            if (startDateDisplay) startDateDisplay.innerText = lastWeekStr;
            if (endDateDisplay) endDateDisplay.innerText = todayStr;
        }
        return { lastWeekStr, todayStr };
    }

    // --- [2] 금액 계산 함수 (1/n 계산) ---
    function updateAmount() {
        let totalExpense = 0;
        const checkedTxs = document.querySelectorAll('input[name="selectedTrans"]:checked');
        checkedTxs.forEach(cb => {
            const amt = parseInt(cb.getAttribute('data-amount')) || 0;
            totalExpense += amt;
        });

        const checkedMembers = document.querySelectorAll('input[name="settleMember"]:checked');
        const memberCount = checkedMembers.length;
        const perPersonAmount = memberCount > 0 ? Math.floor(totalExpense / memberCount) : 0;

        if (finalAmountDisplay) {
            finalAmountDisplay.innerText = perPersonAmount.toLocaleString() + '원';
        }
    }

    // --- [3] 클릭 영역 강제 호출 (클릭 안되는 문제 해결) ---
    document.querySelectorAll('.date-click-area').forEach(area => {
        area.addEventListener('click', function() {
            const targetId = this.getAttribute('data-target');
            const input = document.getElementById(targetId);
            if (input && typeof input.showPicker === 'function') {
                input.showPicker(); // 시스템 달력 호출
            }
        });
    });

    // --- [4] 이벤트 리스너 등록 ---
    document.addEventListener('change', function(e) {
        if (e.target.name === 'selectedTrans' || e.target.name === 'settleMember') {
            updateAmount();
        }
    });

    [startDateInput, endDateInput].forEach(input => {
        if (!input) return;
        input.addEventListener('change', function() {
            const display = document.getElementById(this.id + '-display');
            if (display) display.innerText = this.value;
            loadExpenses(groupId, startDateInput.value, endDateInput.value);
        });
    });

    // --- [5] 지출 내역 비동기 로드 함수 ---
    async function loadExpenses(groupId, startDate, endDate) {
        if (!groupId) return;
        try {
            const response = await authFetch(`/api/group/${groupId}/expenses?startDate=${startDate}&endDate=${endDate}`);
            const data = await response.json();
            
            const container = document.getElementById('transaction-container');
            if (!container) return;
            container.innerHTML = ''; 

            if (!data || data.length === 0) {
                container.innerHTML = '<p class="text-xs text-slate-400 py-10 text-center font-medium">선택된 기간에 지출 내역이 없습니다.</p>';
                updateAmount();
                return;
            }

            data.forEach(expense => {
                const id = expense.expenseId || expense.EXPENSE_ID;
                const amt = expense.amount || expense.AMOUNT || 0;
                const name = expense.merchantName || expense.MERCHANT_NAME || '상점명 없음';
                const date = expense.createdAt || expense.CREATED_AT || '';

                const html = `
                    <label class="group relative flex items-center gap-4 p-4 bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm cursor-pointer transition-all w-full has-[:checked]:border-blue-600 has-[:checked]:ring-1 has-[:checked]:ring-blue-600">
                        <input type="checkbox" name="selectedTrans" value="${id}" data-amount="${amt}"
                               class="w-5 h-5 rounded-full border-slate-300 text-blue-600 focus:ring-blue-500">
                        <div class="flex-1">
                            <p class="text-sm font-bold text-slate-800 dark:text-slate-100 group-has-[:checked]:text-blue-600 transition-colors">${name}</p>
                            <p class="text-[11px] text-slate-400">${date}</p>
                        </div>
                        <p class="text-base font-black text-blue-600">${Number(amt).toLocaleString()}원</p>
                    </label>`;
                container.insertAdjacentHTML('beforeend', html);
            });
            updateAmount(); 
        } catch (error) {
            console.error("데이터 로드 실패:", error);
        }
    }

    // --- [6] 전체 선택 버튼 설정 ---
    function setupSelectAll(btnId, name) {
        const btn = document.getElementById(btnId);
        if (!btn) return;
        btn.onclick = function() {
            const cbs = document.querySelectorAll(`input[name="${name}"]`);
            const anyUnchecked = Array.from(cbs).some(cb => !cb.checked);
            cbs.forEach(cb => cb.checked = anyUnchecked);
            updateAmount();
        };
    }

    setupSelectAll('select-all-transactions', 'selectedTrans');
    setupSelectAll('select-all-members', 'settleMember');

    // --- [7] 실행 순서 ---
    const dates = initDefaultDates(); // 1. 날짜 초기화
    if(groupId) {
        loadExpenses(groupId, dates.lastWeekStr, dates.todayStr); // 2. 내역 로드
    }
    updateAmount(); // 3. 금액 합계 초기화
});

// 정산 멤버 전체 선택 (전역)
function toggleSettleMembers(btn) {
    const checkboxes = document.querySelectorAll('.settle-checkbox');
    const allChecked = Array.from(checkboxes).every(cb => cb.checked);
    checkboxes.forEach(cb => {
        cb.checked = !allChecked;
    });
    btn.innerText = allChecked ? "전체 선택" : "전체 해제";
    
    const event = new Event('change', { bubbles: true });
    if(checkboxes[0]) checkboxes[0].dispatchEvent(event);
}
// 정산 알림 보내기 함수
async function sendSettlementAlarm() {
    const mainContainer = document.getElementById('main-container');
    const groupId = mainContainer?.dataset.groupId;

    // 체크된 지출 내역 금액 합산
    let totalAmount = 0;
    const selectedExpenses = document.querySelectorAll('input[name="selectedTrans"]:checked');
    selectedExpenses.forEach(cb => {
        totalAmount += parseInt(cb.getAttribute('data-amount')) || 0;
    });

    // 체크된 뫇들의 ID 수집
    const selectedMemberIds = Array.from(document.querySelectorAll('input[name="settleMember"]:checked'))
                                   .map(cb => parseInt(cb.value));

    if (selectedExpenses.length === 0) {
        alert("정산할 지출 내역을 선택해주세요.");
        return;
    }

    if (selectedMemberIds.length === 0) {
        alert("알림을 받을 뫇들을 선택해주세요.");
        return;
    }

    const perPerson = Math.floor(totalAmount / selectedMemberIds.length);
    const title = `정산 요청 (${totalAmount.toLocaleString()}원 / ${selectedMemberIds.length}명)`;

    if (!confirm(`선택한 ${selectedMemberIds.length}명에게 1인당 ${perPerson.toLocaleString()}원 정산 알림을 보낼까요?`)) return;

    try {
        const response = await authFetch(`/api/group/send-notification`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                groupId:     parseInt(groupId),
                memberIds:   selectedMemberIds,
                totalAmount: totalAmount,
                title:       title,
                message:     `[정산 요청] ${title} — 1인당 ${perPerson.toLocaleString()}원을 확인해주세요!`
            })
        });

        if (response.ok) {
            alert("\u2705 정산 알림이 전송되었습니다!\n정산 관리 페이지에서 내부 현황을 확인해주세요.");
        } else {
            const err = await response.json();
            alert("\u274c 전송 실패: " + (err.message || "서버 오류"));
        }
    } catch (error) {
        console.error("오류:", error);
        alert("오류가 발생했습니다.");
    }
}