'use strict';
(() => {
  const list = document.querySelector('#mail-list'), message = document.querySelector('#mail-message');
  function text(tag, value, className) { const node = document.createElement(tag); node.textContent = value; if (className) node.className = className; return node; }
  async function refresh() {
    try {
      const email = document.querySelector('#mail-email').value;
      const response = await fetch(`/api/dev/mailbox?${new URLSearchParams(email ? { email } : {})}`, { cache: 'no-store' });
      const mails = await response.json(); if (!response.ok) throw new Error(mails.message || '메일함에 연결하지 못했습니다.');
      list.replaceChildren(); message.textContent = mails.length ? `${mails.length}개의 인증 메일이 있어요.` : '아직 받은 메일이 없어요. 회원가입 후 다시 확인해 주세요.';
      for (const mail of mails) {
        const card = text('article', '', 'mail-card');
        card.append(text('h2', mail.subject), text('p', `받는 사람: ${mail.to}`), text('p', new Date(mail.sentAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })));
        const url = new URL(mail.link);
        if (url.origin === location.origin) { const anchor = text('a', '이메일 인증하기 →', 'button button-primary'); anchor.href = mail.link; anchor.target = '_blank'; anchor.rel = 'noopener'; card.append(anchor); }
        else card.append(text('p', '인증 링크 주소와 현재 서버 주소가 달라요. LSRS_PUBLIC_BASE_URL 설정을 확인해 주세요.'));
        list.append(card);
      }
    } catch (error) { message.textContent = error.message; }
  }
  document.querySelector('#mail-filter').addEventListener('submit', event => { event.preventDefault(); refresh(); });
  setInterval(() => { if (!document.hidden) refresh(); }, 10000); refresh();
})();
