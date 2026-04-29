/**
 * KKIRI - 그룹 멤버 관리 스크립트 (API 연동 버전)
 */

document.addEventListener('DOMContentLoaded', () => {
    // HTML의 main 태그에 심어둔 data-group-id를 가져옵니다.
    const mainContainer = document.getElementById('main-container');
    const groupId = mainContainer ? mainContainer.dataset.groupId : null;

    if (groupId) {
		// 1. 현재 그룹의 멤버 목록 조회
		fetchGroupMembers(groupId);
		// 2. 상단 모임 전환 셀렉트 박스 초기화
		initGroupSwitcher(groupId);
    } else {
        console.error("그룹 ID를 찾을 수 없습니다.");
    }
	
	// 모임 전환 이벤트 리스너
	    const switcher = document.getElementById('group-switcher');
	    if (switcher) {
	        switcher.addEventListener('change', (e) => {
	            const selectedGroupId = e.target.value;
	            if (selectedGroupId) {
	                // 선택한 그룹으로 페이지 이동
	                location.href = `/groupmembers?groupId=${selectedGroupId}`;
	            }
	      });
	  }
});


/**
 * 모임 전환 스위처 초기화 (내가 가입한 전체 그룹 목록 가져오기)
 */
async function initGroupSwitcher(currentGroupId) {
    const token = localStorage.getItem('accessToken');
    const switcher = document.getElementById('group-switcher');
    if (!switcher) return;

    try {
        const response = await authFetch('/api/group/my-groups', {
            headers: { 'accessToken': token }
        });

        if (response.ok) {
            const groups = await response.json();
            
            // 초기화 (기본 문구 유지)
            switcher.innerHTML = '<option value="" disabled>모임 전환하기</option>';

            groups.forEach(group => {
                const option = document.createElement('option');
                option.value = group.groupId;
                option.text = group.groupName;
                
                // 현재 보고 있는 그룹이면 선택 상태로
                if (group.groupId == currentGroupId) {
                    option.selected = true;
                }
                switcher.appendChild(option);
            });
        }
    } catch (error) {
        console.error("모임 목록 로드 실패:", error);
    }
}


/**
 * 멤버 목록 조회 (GET 요청)
 */
/**
 * 멤버 목록 조회 (GET 요청)
 */
async function fetchGroupMembers(groupId) {
    const container = document.getElementById('member-container');
    const countElement = document.getElementById('member-count');
    const token = localStorage.getItem('accessToken');

    try {
        const response = await authFetch(`/api/group/${groupId}/members`, {
            method: 'GET',
            headers: {
                'accessToken': token,
                'Content-Type': 'application/json'
            }
        });

        // 에러 처리 로직 (기존과 동일)
        if (response.status === 401) { alert("로그인이 필요합니다."); location.href = "/login"; return; }
        if (response.status === 403) { alert("접근 권한이 없습니다."); location.href = "/main"; return; }
        if (!response.ok) throw new Error('서버 응답 오류');
        
        const members = await response.json();
        countElement.innerText = members.length;
        
        if (members.length === 0) {
            container.innerHTML = `<p class="text-center text-slate-400 py-10">참여 중인 멤버가 없습니다.</p>`;
            return;
        }

        // 🚩 [핵심] 서버가 알려준 "나의 방장 여부" 확인
        // 리스트 중 아무 객체나 꺼내서 isHostUser 값을 확인합니다.
        const amIHost = members.some(member => member.hostUser === true || member.isHostUser === true);

        container.innerHTML = members.map(member => {
            // 1. 이 행의 유저가 방장인지 확인
            const isTargetHost = (member.groupRole === 'HOST');
            
            // 2. 🚩 [추방 버튼 노출 로직 수정]
            // 조건: (내가 방장인가?) AND (상대방은 방장이 아닌가?)
            let actionArea = "";
            
            if (isTargetHost) {
                // 이 사람이 방장이면 (나 포함) '방패 아이콘' 표시
                actionArea = `
                    <div class="pr-3">
                        <span class="material-symbols-outlined text-emerald-400/50 text-xl font-variation-fill">verified_user</span>
                    </div>`;
            } else if (amIHost) {
                // 🚩 내가 방장이고, 상대방은 일반 멤버일 때만 '추방 버튼' 표시
                actionArea = `
                    <div class="flex gap-1">
                        <button onclick="kickMember(${groupId}, ${member.userId}, '${member.userName}')" 
                                title="멤버 추방"
                                class="w-9 h-9 flex items-center justify-center rounded-xl hover:bg-red-50 dark:hover:bg-red-900/20 text-slate-300 hover:text-red-500 transition-all">
                            <span class="material-symbols-outlined text-xl">group_remove</span>
                        </button>
                    </div>`;
            } else {
                // 내가 일반 멤버라면 다른 멤버의 추방 버튼은 아예 안 보임 (빈 공간 처리)
                actionArea = `<div class="w-9"></div>`;
            }

            return `
                <div class="bg-white dark:bg-slate-900 p-4 rounded-[1.5rem] shadow-sm flex items-center gap-4 border border-transparent hover:border-emerald-500/30 transition-all group animate-in fade-in duration-300">
                    <div class="w-12 h-12 bg-slate-100 dark:bg-slate-800 rounded-2xl overflow-hidden flex items-center justify-center border border-slate-50 dark:border-slate-800">
                        ${member.profileImg ? 
                            `<img src="${member.profileImg}" class="w-full h-full object-cover" />` : 
                            `<span class="material-symbols-outlined text-slate-400">person</span>`}
                    </div>
                    
                    <div class="flex-1">
                        <div class="flex items-center gap-2">
                            <p class="font-bold text-sm">${member.userName}</p>
                            <span class="text-[9px] ${isTargetHost ? 'bg-purple-100 text-purple-600' : 'bg-blue-50 text-blue-500'} px-1.5 py-0.5 rounded font-bold uppercase tracking-tighter">
                                ${isTargetHost ? '방장' : '멤버'}
                            </span>
                        </div>
                        <p class="text-[10px] text-slate-400 font-medium">${member.userEmail} · ${formatDate(member.joinedAt)} 가입</p>
                    </div>

                    ${actionArea}
                </div>
            `;
        }).join('');

    } catch (error) {
        console.error("멤버 로드 실패:", error);
        container.innerHTML = `<p class="text-center text-rose-500 py-10">데이터를 불러오는 중 오류가 발생했습니다.</p>`;
    }
}


/**
 * 날짜 포맷팅 함수 (Timestamp -> yyyy.MM.dd)
 */
function formatDate(timestamp) {
    if (!timestamp) return "";
    const date = new Date(timestamp);
    return `${date.getFullYear()}.${(date.getMonth() + 1).toString().padStart(2, '0')}.${date.getDate().toString().padStart(2, '0')}`;
}

/**
 * 멤버 추방 (실제 서버 연동 버전)
 */
async function kickMember(groupId, userId, userName) {
    // 1. 사용자 확인 (방장이 실수로 누르는 것 방지)
    if (!confirm(`${userName}님을 그룹에서 제외하시겠습니까?`)) return;

    try {
        // 2. [수정] 실제 백엔드 API 호출
        // DELETE 방식으로 호출하며, URL에 groupId와 userId를 담아 보냅니다.
        const response = await authFetch(`/api/group/${groupId}/members/${userId}`, { 
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json',
                // 만약 보안 토큰이 필요하다면 아래 줄 유지, 없으면 삭제하세요.
                'accessToken': localStorage.getItem('accessToken') 
            }
        });

        // 3. 응답 결과 처리
        if (response.ok) {
            alert(`${userName}님이 성공적으로 제외되었습니다.`);
            
            // 4. [중요] 새로고침(reload) 대신 목록만 다시 불러오기
            // 페이지 전체를 새로고침하면 사용자 경험이 떨어지므로, 
            // 아까 만들어둔 목록 조회 함수를 다시 실행하는 게 훨씬 깔끔합니다.
            fetchGroupMembers(groupId); 
        } else {
            // 서버에서 에러 메시지를 보냈을 경우 처리
            const errorText = await response.text();
            alert("제외 실패: " + (errorText || "서버 오류가 발생했습니다."));
        }
        
    } catch (error) {
        console.error("추방 요청 중 네트워크 오류:", error);
        alert("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }
}