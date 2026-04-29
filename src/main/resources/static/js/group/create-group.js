/**
 * KKIRI 모임 생성 1단계 스크립트
 * 기능: 모임명, 목적, 체제 선택 및 세션 저장 후 다음 단계 이동
 */
document.addEventListener('DOMContentLoaded', function() {
    // [1] 요소 선택
    const groupNameInput = document.getElementById('group-name');
    const createBtn = document.getElementById('create-btn'); 
    const purposeButtons = document.querySelectorAll('.purpose-btn');
    const systemTabs = document.querySelectorAll('.system-tab');

    // [2] 모임 목적(Category) 선택 이벤트 설정
    purposeButtons.forEach(btn => {
        btn.onclick = function() {
            // 모든 버튼의 활성화 스타일 제거 (기본 스타일로 초기화)
            purposeButtons.forEach(b => {
                b.classList.remove('bg-primary', 'text-white', 'font-black', 'shadow-md', 'shadow-primary/20');
                b.classList.add('bg-slate-100', 'dark:bg-slate-800', 'text-slate-500', 'font-bold');
            });
            
            // 클릭된 버튼에만 활성화 스타일 적용
            this.classList.remove('bg-slate-100', 'dark:bg-slate-800', 'text-slate-500', 'font-bold');
            this.classList.add('bg-primary', 'text-white', 'font-black', 'shadow-md', 'shadow-primary/20');
        };
    });

    // [3] 모임 체제(System) 선택 이벤트 설정 (매월 vs 일회성)
    systemTabs.forEach(btn => {
        btn.onclick = function() {
            // 모든 탭의 활성화 스타일 제거
            systemTabs.forEach(b => {
                b.classList.remove('font-black', 'text-primary', 'bg-white', 'dark:bg-slate-700', 'shadow-sm');
                b.classList.add('font-bold', 'text-slate-400');
            });
            
            // 클릭된 탭에만 활성화 스타일 적용
            this.classList.remove('font-bold', 'text-slate-400');
            this.classList.add('font-black', 'text-primary', 'bg-white', 'dark:bg-slate-700', 'shadow-sm');
        };
    });

    // [4] 생성 버튼 클릭 시 로직
    if (createBtn) {
        createBtn.onclick = function(e) {
            e.preventDefault(); // 기본 폼 제출 동작 방지
            
            // 데이터 수집
            const groupName = groupNameInput.value;
            
            // 현재 파란색 배경(bg-primary)인 목적 버튼의 텍스트 가져오기
            const activePurposeBtn = document.querySelector('.purpose-btn.bg-primary');
            const category = activePurposeBtn ? activePurposeBtn.innerText.trim() : "";

            // 현재 파란색 글씨(text-primary)인 체제 탭의 data-value 값 가져오기
            const activeSystemTab = document.querySelector('.system-tab.text-primary');
            const systemType = activeSystemTab ? activeSystemTab.getAttribute('data-value') : "continuous";

            // 유효성 검사 (모임명 입력 확인)
            if (!groupName.trim()) {
                alert("모임 이름을 입력해주세요.");
                groupNameInput.focus();
                return;
            }
            
            // 유효성 검사 (목적 선택 확인)
            if (!category) {
                alert("모임 목적을 선택해주세요.");
                return;
            }

            // --- 세션 스토리지 저장 (2단계 계좌 개설 페이지에서 꺼내 쓸 데이터) ---
            sessionStorage.setItem('tempGroupName', groupName);
            sessionStorage.setItem('tempGroupCategory', category);
            sessionStorage.setItem('tempGroupSystem', systemType);

            // 확인용 콘솔로그 (개발자 도구에서 확인 가능)
            console.log("저장된 데이터:", {
                name: groupName,
                category: category,
                system: systemType
            });

            // 다음 단계 페이지로 이동 (컨트롤러 매핑 주소 확인)
            window.location.href = '/passbook'; 
        };
    }
});