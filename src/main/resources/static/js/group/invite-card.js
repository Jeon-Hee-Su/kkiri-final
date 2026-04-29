document.addEventListener('DOMContentLoaded', function() {
    initInviteAcceptPage();
});

async function initInviteAcceptPage() {
    const urlParams = new URLSearchParams(window.location.search);
    const inviteCode = urlParams.get('code');

    if (!inviteCode) {
        console.error("초대 코드가 없습니다.");
        return;
    }

    try {
        const response = await fetch(`/api/groups/by-code/${inviteCode}`, {
            credentials: 'include'
        });
        if (response.ok) {
            const group = await response.json();
            document.getElementById('display-group-name').innerText = group.groupName;
            document.getElementById('display-host-name').innerText = group.hostName || "방장님";
            document.getElementById('member-count-text').innerText = `${group.memberCount || 1}명 참여 중`;
            updateHeroImage(group.groupPurpose || group.purpose);
        }
    } catch (e) {
        console.error("서버 데이터 로딩 실패:", e);
    }
}

function updateHeroImage(purpose) {
    const bgImages = {
        '여행': 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=600',
        '취미': 'https://images.unsplash.com/photo-1517404212738-19266ad94814?q=80&w=600',
        '데이트': 'https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?q=80&w=600',
        '동호회': 'https://images.unsplash.com/photo-1521737604893-d14cc237f11d?q=80&w=600'
    };
    const selectedBg = bgImages[purpose] || 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?q=80&w=600';
    const heroBg = document.getElementById('hero-bg');
    if (heroBg) heroBg.style.backgroundImage = `url("${selectedBg}")`;
}

async function goToLinkAccount() {
    const urlParams = new URLSearchParams(window.location.search);
    const code = urlParams.get('code');

    if (!code) {
        alert("초대 정보가 올바르지 않습니다.");
        return;
    }

    try {
        // 1. 로그인 여부 확인
        const tokenRes = await fetch('/api/auth/token-status', {
            method: 'POST',
            credentials: 'include'
        });

        if (!tokenRes.ok || !(await tokenRes.json()).valid) {
            // 비로그인 → 로그인 페이지로
            alert("모임에 참여하시려면 먼저 로그인이 필요합니다.");
            document.cookie = `pendingInviteCode=${code}; path=/; max-age=600; SameSite=Lax`;
            sessionStorage.setItem("pendingInviteCode", code);
            location.href = "/auth/login";
            return;
        }

        // 2. 초대코드로 그룹 정보 조회
        const groupRes = await fetch(`/api/groups/by-code/${code}`, {
            credentials: 'include'
        });

        if (!groupRes.ok) {
            alert("유효하지 않은 초대 코드입니다.");
            return;
        }

        const group = await groupRes.json();
        const groupId = group.groupId;

        // 3. 현재 유저가 이미 해당 그룹 멤버인지 확인
        const meRes = await authFetch('/api/user/me', { credentials: 'include' });
        if (!meRes.ok) {
            alert("사용자 정보를 가져올 수 없습니다.");
            return;
        }

        const me = await meRes.json();

        // 4. 그룹 멤버 목록에서 현재 유저 확인
        const membersRes = await authFetch(`/api/group/${groupId}/members`, {
            credentials: 'include'
        });

        if (membersRes.ok) {
            const members = await membersRes.json();
            const alreadyMember = members.some(m => m.userId === me.userId);

            if (alreadyMember) {
                // 이미 멤버(방장 포함) → 해당 그룹 메인으로 이동
                alert("이미 해당 모임에 참여 중입니다.");
                location.href = `/group/detail/${groupId}`;
                return;
            }
        }

        // 5. 신규 멤버 → 계좌 연동 페이지로
        location.href = `/link-account?code=${code}`;

    } catch (e) {
        console.error("로그인 상태 확인 실패:", e);
        alert("모임에 참여하시려면 먼저 로그인이 필요합니다.");
        document.cookie = `pendingInviteCode=${code}; path=/; max-age=600; SameSite=Lax`;
        sessionStorage.setItem("pendingInviteCode", code);
        location.href = "/auth/login";
    }
}