/**
 * KKIRI - 그룹 설정 관리 통합 스크립트 (수정 버전)
 */

document.addEventListener('DOMContentLoaded', function() {
    console.log("KKIRI Group Settings Loaded! 🚀");
    
    // 초기 실행 로직이 필요하다면 여기에 작성
});

/**
 * [공통] groupId 추출 함수
 */
function getGroupId() {
    const groupIdEl = document.getElementById('groupId');
    return (groupIdEl && groupIdEl.value !== "0") ? groupIdEl.value : null;
}

/**
 * [기능 1] 섹션: 정보 관리 (모임 이름, 카테고리 수정)
 */
async function updateGroupInfo() {
    const groupId = getGroupId();
    const groupName = document.getElementById('groupName').value.trim();
    const category = document.getElementById('groupCategory').value; // hidden input 값

    if (!groupId) { alert("그룹 정보를 찾을 수 없습니다."); return; }
    if (!groupName) { alert("모임 이름을 입력해주세요."); return; }

    if (!confirm("모임 정보를 수정하시겠습니까?")) return;

    try {
        const response = await authFetch('/api/group/update-info', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ 
                groupId: groupId, 
                groupName: groupName,
                category: category
            })
        });

        if (response.ok) {
            alert("정보가 수정되었습니다! ✨");
            location.reload(); 
        } else {
            alert("수정에 실패했습니다.");
        }
    } catch (error) {
        console.error("Update Info Error:", error);
        alert("서버 통신 중 오류가 발생했습니다.");
    }
}

/**
 * [기능 2] 섹션: 회비 규칙 저장
 */
async function updateFeeRules() {
    const groupId = getGroupId();
    const regularDay = document.getElementById('regularDay').value;
    const regularAmount = document.getElementById('regularAmount').value;
    const penaltyDay = document.getElementById('penaltyDay').value;
    const penaltyAmount = document.getElementById('penaltyAmount').value;

    if (!confirm("회비 및 벌칙금 규칙을 저장하시겠습니까?")) return;

    try {
        const response = await authFetch('/api/group/update-rules', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                groupId: groupId,
                regularDay: regularDay,
                regularAmount: regularAmount,
                penaltyDay: penaltyDay,
                penaltyAmount: penaltyAmount
            })
        });

        if (response.ok) {
            alert("회비 규칙이 적용되었습니다! 💸");
        } else {
            alert("저장에 실패했습니다. VO 필드 구성을 확인하세요.");
        }
    } catch (error) {
        console.error("Update Rules Error:", error);
    }
}

/**
 * [기능 3] 섹션: 알림 쏘기 (미납자 알림)
 */

async function sendDueNotification(event) {
    // 2. 브라우저의 기본 동작(주소창에 :1 등을 붙이는 행위)을 즉시 중단시킵니다.
    if (event) event.preventDefault();

    const selectedMembers = Array.from(document.querySelectorAll('input[name="selectedMember"]:checked'))
                                 .map(el => el.value);

    if (selectedMembers.length === 0) {
        alert("알림을 보낼 멤버를 선택해주세요.");
        return;
    }

    if (!confirm(`${selectedMembers.length}명에게 미납 알림을 보낼까요?`)) return;

    try {
        // 3. 주소 뒤에 혹시 모를 간섭을 막기 위해 깔끔하게 정리
        const response = await authFetch('/api/group/send-notification', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ 
                memberIds: selectedMembers,
                groupId: document.getElementById('groupId').value // groupId도 같이 보내주는게 안전합니다.
            })
        });

        if (response.ok) {
            alert("알림이 성공적으로 전송되었습니다! 🔔");
        } else {
            alert("서버 응답 오류 (404/500)");
        }
    } catch (error) {
        console.error(error);
        alert("전송 중 네트워크 오류가 발생했습니다.");
    }
}

/**
 * [기능 4] 섹션: 그룹 삭제 (Danger Zone)
 */
async function deleteGroup() {
    const groupId = getGroupId();
    if (!confirm("정말로 이 그룹을 삭제하시겠습니까?\n이 작업은 되돌릴 수 없으며 모든 데이터가 삭제됩니다.")) return;

    try {
        const response = await authFetch(`/api/group/delete/${groupId}`, { method: 'DELETE' });
        if (response.ok) {
            alert("그룹이 삭제되었습니다.");
            location.href = "/group/detail/0"; // 그룹 삭제 후 첫 그룹으로 
        } else {
            alert("삭제 권한이 없거나 오류가 발생했습니다.");
        }
    } catch (error) {
        console.error("Delete Error:", error);
    }
}

/**
 * [UI 제어] 드롭다운 선택 로직
 */
function toggleDropdown(el) {
    const menu = el.nextElementSibling;
    menu.classList.toggle('hidden');
}

function selectOption(el, value, targetId) {
    // 1. 값 반영
    const input = document.getElementById(targetId);
    if (input) input.value = value;

    // 2. UI 텍스트 변경
    const selectedText = el.closest('.custom-dropdown').querySelector('.selected-text');
    if (selectedText) {
        selectedText.innerText = el.innerText;
    }

    // 3. 드롭다운 닫기
    el.closest('.dropdown-menu').classList.add('hidden');
}
/**
 * 미납자 알림 전송 함수
 */
/**
 * 미납자 알림 전송 (선택 전송 + 전체 전송 통합)
 */
async function sendDueNotification(isAll = false) {
    const groupId = document.getElementById('groupId').value;
    const allCheckBoxes = document.querySelectorAll('input[name="selectedMember"]');
    
    // 1. 전체 전송인 경우 모든 체크박스 강제 체크
    if (isAll) {
        allCheckBoxes.forEach(cb => cb.checked = true);
    }

    // 2. 체크된 ID만 수집
    const checkedBoxes = document.querySelectorAll('input[name="selectedMember"]:checked');
    const selectedIds = Array.from(checkedBoxes).map(cb => parseInt(cb.value));

    if (selectedIds.length === 0) {
        alert("알림을 보낼 멤버를 선택해주세요.");
        return;
    }

    const confirmMsg = isAll ? "전체 멤버에게 알림을 보낼까요?" : `${selectedIds.length}명에게 알림을 보낼까요?`;
    if (!confirm(confirmMsg)) return;

    try {
        const response = await authFetch('/api/settlement/alarm', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ 
                memberIds: selectedIds, // 컨트롤러의 키값과 맞춤
                groupId: groupId,
                message: "미납된 회비가 있습니다. 확인 부탁드립니다! 😊"
            })
        });

        if (response.ok) {
            alert("알림이 성공적으로 전송되었습니다! 🔔");
            // 성공 후 체크 해제
            allCheckBoxes.forEach(cb => cb.checked = false);
        } else {
            alert("전송 실패 (서버 상태를 확인하세요)");
        }
    } catch (error) {
        console.error("API Error:", error);
        alert("네트워크 오류가 발생했습니다.");
    }
}
/**
 * [UI 제어] 멤버 전체 선택 / 해제
 * @param {HTMLInputElement} selectAllCheckbox - '전체 선택' 체크박스 요소
 */
function toggleAllMembers(selectAllCheckbox) {
    // name="selectedMember"를 가진 모든 체크박스를 찾습니다.
    const memberCheckboxes = document.querySelectorAll('input[name="selectedMember"]');
    
    memberCheckboxes.forEach(cb => {
        // '전체 선택' 체크박스의 상태(checked true/false)를 모든 멤버 체크박스에 동일하게 적용
        cb.checked = selectAllCheckbox.checked;
    });
    
    console.log(`전체 선택 상태: ${selectAllCheckbox.checked}, 대상 수: ${memberCheckboxes.length}`);
}

// HTML의 onchange="toggleAllMembers(this)"와 연결하기 위해 전역 등록
window.toggleAllMembers = toggleAllMembers;

// 전역 등록 (HTML에서 onclick으로 쓰기 위함)
window.sendDueNotification = sendDueNotification;



// 전역 공개
window.updateGroupInfo = updateGroupInfo;
window.updateFeeRules = updateFeeRules;
window.sendDueNotification = sendDueNotification;
window.deleteGroup = deleteGroup;
window.toggleDropdown = toggleDropdown;
window.selectOption = selectOption;
window.sendDueNotification = sendDueNotification;