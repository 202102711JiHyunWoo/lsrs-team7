'use strict';
(async () => {
  const token = new URL(location.href).searchParams.get('token');
  history.replaceState({}, '', '/verify-email');
  const title = document.querySelector('#verify-title'), message = document.querySelector('#verify-message');
  try {
    if (!token) throw new Error('인증 메일에 있는 링크를 다시 열어 주세요.');
    const response = await fetch('/api/email-verifications', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }) });
    const result = await response.json(); if (!response.ok) throw new Error(result.message || '인증을 완료하지 못했습니다.');
    title.textContent = '이메일 인증이 완료되었어요.';
    message.textContent = '이제 로그인하고 원하는 좌석을 예약할 수 있어요.';
    document.querySelector('#verify-symbol').textContent = '✓';
  } catch (error) { title.textContent = '인증 상태를 확인해 주세요.'; message.textContent = error.message; document.querySelector('#verify-symbol').textContent = '!'; }
})();
