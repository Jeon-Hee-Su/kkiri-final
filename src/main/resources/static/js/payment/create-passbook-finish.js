/**
 * 모임 통장 개설 완료 페이지 전용 스크립트
 */
document.addEventListener('DOMContentLoaded', () => {
    // 1. 필요한 HTML 요소 셀렉트
    const accountNoDisplay = document.getElementById('display-account-number');
    const startBtn = document.getElementById('start-btn');
    const copyBtn = document.getElementById('copy-btn');

    // 2. 계좌번호 복사 기능
    // 서버(Thymeleaf)가 화면에 뿌려준 계좌번호 텍스트를 그대로 복사합니다.
    if (copyBtn && accountNoDisplay) {
        copyBtn.onclick = function() {
            // '계좌 정보를 불러올 수 없습니다' 같은 문구는 복사되지 않도록 방어 로직
            const textToCopy = accountNoDisplay.innerText.trim();
            
            if (textToCopy && !textToCopy.includes("불러올 수 없습니다")) {
                navigator.clipboard.writeText(textToCopy).then(() => {
                    // 버튼 텍스트 변경 피드백 (2초 후 원복)
                    const originalText = copyBtn.innerText;
                    copyBtn.innerText = '복사됨';
                    copyBtn.classList.add('bg-green-100', 'text-green-600'); // 시각적 효과 추가(선택)
                    
                    setTimeout(() => { 
                        copyBtn.innerText = originalText; 
                        copyBtn.classList.remove('bg-green-100', 'text-green-600');
                    }, 2000);
                }).catch(err => {
                    console.error('복사 실패:', err);
                });
            }
        };
    }

    // 3. 모임 시작하기 버튼 (초대 페이지로 이동)
    // HTML 버튼의 'data-group-id' 속성에 담긴 ID를 읽어와서 쿼리 스트링으로 전달합니다.
    if (startBtn) {
        startBtn.onclick = function() {
            // Thymeleaf의 th:data-group-id="${groupId}" 값을 가져옴
            const groupId = startBtn.getAttribute('data-group-id');

            // groupId가 정상적으로 존재할 때만 파라미터를 붙여서 이동
            if (groupId && groupId !== 'null' && groupId !== '') {
                window.location.href = `/invite?groupId=${groupId}`; 
            } else {
                // ID가 없으면 안전하게 기본 초대 페이지나 메인으로 이동
                console.warn("전달된 그룹 ID가 없습니다. 기본 페이지로 이동합니다.");
                window.location.href = '/invite'; 
            }
        };
    }
});