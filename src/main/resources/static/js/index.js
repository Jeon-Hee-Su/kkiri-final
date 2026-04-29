/**
 * index.js - 메인 페이지 인터랙션 제어
 */
document.addEventListener('DOMContentLoaded', function() {
    
    // 1. 요소 선택
    const createBtn = document.getElementById('btn-group-create-start'); // 그룹 생성 버튼
    const checkBtn = document.getElementById('btn-group-check');         // 그룹 확인 버튼
    const groupModal = document.getElementById('group-list-modal');     // 모달 전체 레이어
    const modalContent = document.getElementById('modal-content');     // 모달 본체
    const groupListContainer = document.getElementById('modal-group-list'); // 그룹 리스트 박스
    const closeButtons = document.querySelectorAll('.btn-close-modal'); // 닫기 버튼들

    // --- 그룹 생성 버튼 클릭 이벤트 ---
    if (createBtn) {
        createBtn.addEventListener('click', async (e) => {
            e.preventDefault();
            e.stopImmediatePropagation(); // 다른 스크립트 간섭 차단

            console.log("비동기 그룹 생성 체크 시작...");

            try {
                // 비동기로 서버에 권한 체크
                await authFetch('/api/group/check-permission');
                window.location.href = '/creategroup';
            } catch (error) {
                console.error("체크 중 오류 발생, 강제 이동합니다.");
                window.location.href = '/creategroup';
            }
        });
    }

    // --- 그룹 확인 모달 제어 ---
    
    // 모달 열기
    if (checkBtn) {
        checkBtn.addEventListener('click', function() {
            groupModal.classList.remove('hidden');
            groupModal.classList.add('flex');
            
            // 애니메이션 (살짝 커지면서 나타남)
            setTimeout(() => {
                modalContent.classList.remove('scale-95', 'opacity-0');
                modalContent.classList.add('scale-100', 'opacity-100');
            }, 10);

            fetchMyGroups(); // 서버에서 내 그룹 목록 가져오기
        });
    }

    // 모달 닫기 공통 함수
    function closeModal() {
        modalContent.classList.add('scale-95', 'opacity-0');
        modalContent.classList.remove('scale-100', 'opacity-100');
        setTimeout(() => {
            groupModal.classList.add('hidden');
            groupModal.classList.remove('flex');
        }, 300);
    }

    // 닫기 버튼들 이벤트 연결
    closeButtons.forEach(btn => btn.addEventListener('click', closeModal));

    // 배경 클릭 시 닫기
    if (groupModal) {
        groupModal.addEventListener('click', (e) => {
            if (e.target === groupModal) closeModal();
        });
    }

    // --- [데이터 통신] 그룹 목록 페칭 ---
    async function fetchMyGroups() {
        try {
            // 형님의 API 경로에 맞춰 수정 가능 (예: /api/groups/list)
            const response = await authFetch('/api/groups/my-list');
            
            if (!response.ok) throw new Error("데이터 응답 에러");
            
            const groups = await response.json();
            renderGroups(groups);
            
        } catch (error) {
            console.error('그룹 목록 로드 실패:', error);
            groupListContainer.innerHTML = `
                <div class="py-12 text-center">
                    <span class="material-symbols-outlined !text-5xl text-red-400 mb-2">error</span>
                    <p class="text-slate-500">정보를 불러오지 못했습니다.</p>
                </div>
            `;
        }
    }

	// --- [UI 렌더링] 그룹 목록 생성 ---
	    function renderGroups(groups) {
	        if (!groups || groups.length === 0) {
	            groupListContainer.innerHTML = `
	                <div class="py-12 text-center">
	                    <span class="material-symbols-outlined !text-5xl text-slate-300 mb-2">sentiment_dissatisfied</span>
	                    <p class="text-slate-500 font-medium">참여 중인 그룹이 아직 없네요!</p>
	                </div>
	            `;
	            return;
	        }

	        // 목록 생성 (초대코드 제거, 디자인 심플화)
	        groupListContainer.innerHTML = groups.map(group => `
	            <div class="group-item flex items-center justify-between p-5 mb-3 bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 rounded-2xl hover:border-primary hover:shadow-md transition-all cursor-pointer group" 
	                 onclick="location.href='/group/detail/${group.groupId}'">
	                <div class="flex items-center gap-4">
	                    <div class="w-12 h-12 bg-primary/10 text-primary rounded-xl flex items-center justify-center font-black text-xl group-hover:bg-primary group-hover:text-white transition-all">
	                        ${group.groupName ? group.groupName.substring(0, 1) : 'G'}
	                    </div>
	                    <div>
	                        <h4 class="font-bold text-slate-900 dark:text-white group-hover:text-primary transition-colors text-lg">
	                            ${group.groupName}
	                        </h4>
	                        <div class="flex items-center gap-2 mt-0.5">
	                            <span class="text-xs font-medium text-slate-400 dark:text-slate-500">
	                                ${group.category || '일반 그룹'}
	                            </span>
	                            <span class="w-1 h-1 bg-slate-300 rounded-full"></span>
	                            <span class="text-[10px] text-green-500 font-bold uppercase tracking-wider">Active</span>
	                        </div>
	                    </div>
	                </div>
	                <div class="flex items-center text-slate-300 group-hover:text-primary transition-all translate-x-0 group-hover:translate-x-1">
	                    <span class="material-symbols-outlined">arrow_forward_ios</span>
	                </div>
	            </div>
	        `).join('');
	    }
    // "새 그룹 만들기" 버튼 이동 (모달 하단 버튼)
    const btnGoCreate = document.getElementById('btn-go-create');
    if (btnGoCreate) {
        btnGoCreate.onclick = () => {
            window.location.href = '/creategroup';
        };
    }
});

// fcm용 uuid추가
function getOrCreateUUID() {
	let uuid = localStorage.getItem('device_id');
	
	if(!uuid) {
		uuid =self.crypto.randomUUID();
		
		localStorage.setItem('device_id', uuid);
		console.log("새 기기 식별자 생성 완료", uuid)
	}
	return uuid
}

const Device_Id = getOrCreateUUID();

console.log("현재 기기 id : ", Device_Id);