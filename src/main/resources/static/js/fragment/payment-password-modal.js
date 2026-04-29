/**
 * payment-password-modal.js
 * 결제 비밀번호 확인 모달 공유 스크립트
 *
 * ── 사용법 1: 그룹 비밀번호 서버 검증 후 콜백 실행 ──
 *   openPaymentPasswordModal(groupId, onSuccess)
 *   - groupId  : 검증할 그룹 ID (숫자)
 *   - onSuccess: 검증 성공 시 실행할 콜백 () => {}
 *   예) openPaymentPasswordModal(63, () => submitJoinGroup());
 *
 * ── 사용법 2: 비밀번호를 직접 콜백으로 전달 (계좌 등록 등) ──
 *   openPaymentPasswordModal(null, onSuccess, options)
 *   - groupId  : null 로 전달
 *   - onSuccess: (password) => {} 형태로 비밀번호를 받아서 처리
 *   - options  : { title, subtitle } 텍스트 커스텀 (선택)
 *   예) openPaymentPasswordModal(null, (pw) => registerAccount(pw));
 */

(function () {
    let _groupId    = null;
    let _onSuccess  = null;
    let _password   = '';
    let _isPassMode = false; // true면 비밀번호를 직접 콜백으로 전달

    /** 모달 열기 */
    window.openPaymentPasswordModal = function (groupId, onSuccess, options = {}) {
        _groupId    = groupId;
        _onSuccess  = onSuccess;
        _password   = '';
        _isPassMode = (groupId === null || groupId === undefined);

        // 제목/설명 커스텀
        const titleEl    = document.getElementById('ppm-title');
        const subtitleEl = document.getElementById('ppm-status');
        if (titleEl)    titleEl.textContent    = options.title    || '결제 비밀번호 확인';
        if (subtitleEl) subtitleEl.textContent = options.subtitle || '숫자 6자리를 입력해주세요.';
        if (subtitleEl) subtitleEl.className   = 'text-slate-500 text-sm font-medium mb-10';

        _updateDots();
        document.getElementById('paymentPasswordModal').classList.remove('hidden');
        document.body.style.overflow = 'hidden';
    };

    /** 모달 닫기 */
    window.closePaymentPasswordModal = function () {
        document.getElementById('paymentPasswordModal').classList.add('hidden');
        document.body.style.overflow = '';
        _password = '';
        _updateDots();
    };

    /** 키패드 입력 */
    window.ppmPressKey = function (key) {
        if (key === 'del') {
            _password = _password.slice(0, -1);
            _updateDots();
            return;
        }
        if (_password.length >= 6) return;
        _password += key;
        _updateDots();

        if (_password.length === 6) {
            setTimeout(_handleComplete, 200);
        }
    };

    /** 6자리 완성 처리 */
    async function _handleComplete() {
        if (_isPassMode) {
            // 모드 2: 비밀번호를 직접 콜백으로 전달 (서버 검증 없음)
            const pw = _password;
            closePaymentPasswordModal();
            if (typeof _onSuccess === 'function') _onSuccess(pw);
        } else {
            // 모드 1: 서버에 검증 요청 후 성공 시 콜백
            await _verifyWithServer();
        }
    }

    /** 서버 검증 (그룹 비밀번호) */
    async function _verifyWithServer() {
        try {
            const response = await authFetch('/api/account/verify-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ groupId: _groupId, paymentPassword: _password })
            });

            const result = await response.json();

            if (result.success) {
                closePaymentPasswordModal();
                if (typeof _onSuccess === 'function') _onSuccess();
            } else {
                _password = '';
                _updateDots();
                _setStatus('비밀번호가 일치하지 않습니다. 다시 입력해주세요.', true);
            }
        } catch (e) {
            console.error('비밀번호 검증 오류:', e);
            _password = '';
            _updateDots();
            _setStatus('서버 오류가 발생했습니다. 다시 시도해주세요.', true);
        }
    }

    /** 점 UI 업데이트 */
    function _updateDots() {
        const dots = document.querySelectorAll('#paymentPasswordModal .ppm-dot');
        dots.forEach((dot, i) => {
            if (i < _password.length) {
                dot.classList.add('bg-primary', 'border-primary');
                dot.classList.remove('border-slate-200');
            } else {
                dot.classList.remove('bg-primary', 'border-primary');
                dot.classList.add('border-slate-200');
            }
        });
    }

    /** 상태 메시지 */
    function _setStatus(message, isError) {
        const el = document.getElementById('ppm-status');
        if (!el) return;
        el.textContent = message;
        el.className = isError
            ? 'text-red-500 text-sm font-bold mb-10'
            : 'text-slate-500 text-sm font-medium mb-10';
    }
})();