'use strict';

(() => {
  const $ = (selector) => document.querySelector(selector);
  const state = { user: null, token: sessionStorage.getItem('lsrs.token'), info: null, policy: null,
    seats: [], zone: 'ALL', selected: null, query: null, dirty: true, loaded: false, loading: false,
    generation: 0, reservations: [], confirming: null, scanning: false, scanBusy: false,
    stream: null, cameraEpoch: 0, checkin: null, qrUrl: null, qrGeneration: 0 };
  const names = { AVAILABLE: '예약 가능', MINE: '내 예약', IN_USE: '사용 중', RESERVED: '예약됨' };
  const zoneNames = { QUIET: '집중 열람실', OPEN: '오픈 열람실' };
  let toastTimer;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function showMessage(node, message, error = true) {
    node.textContent = message || ''; node.hidden = !message;
    node.classList.toggle('error', error);
  }
  function toast(message, error = false) {
    clearTimeout(toastTimer); showMessage($('#toast'), message, error);
    toastTimer = setTimeout(() => { $('#toast').hidden = true; }, 5500);
  }
  function parts(value) {
    return Object.fromEntries(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric',
      month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
      .formatToParts(new Date(value)).filter(part => part.type !== 'literal').map(part => [part.type, part.value]));
  }
  function dateOf(value) { const p = parts(value); return `${p.year}-${p.month}-${p.day}`; }
  function timeOf(value) { const p = parts(value); return `${p.hour}:${p.minute}`; }
  function reservationEndTime(reservation) {
    const end = timeOf(reservation.endAt);
    return end === '00:00' && dateOf(reservation.startAt) !== dateOf(reservation.endAt) ? '24:00' : end;
  }
  function dateLabel(value) {
    return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: 'long', day: 'numeric', weekday: 'short' })
      .format(new Date(value));
  }
  function serverNow() {
    if (!state.info) return Date.now();
    return Date.parse(state.info.serverTime) + (state.info.demo ? 0 : Date.now() - state.info.receivedAt);
  }
  function setServerTime(value) {
    if (state.info) { state.info.serverTime = value; state.info.receivedAt = Date.now(); }
    $('#server-clock').textContent = `${dateLabel(value)} ${timeOf(value)}`;
  }
  async function api(path, options = {}) {
    const headers = { Accept: 'application/json' };
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    if (state.token && options.auth !== false) headers.Authorization = `Bearer ${state.token}`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    let response;
    try {
      response = await fetch(path, { method: options.method || 'GET', headers, signal: controller.signal,
        body: options.body === undefined ? undefined : JSON.stringify(options.body), cache: 'no-store' });
    } catch (exception) {
      throw new Error(exception.name === 'AbortError' ? '응답이 지연되고 있어요. 잠시 후 다시 시도해 주세요.' : '서버에 연결하지 못했어요. 연결 상태를 확인한 후 다시 시도해 주세요.');
    } finally { clearTimeout(timeout); }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      if (response.status === 401 && options.auth !== false && state.user) {
        signOut(false); toast('로그인이 만료되었습니다. 다시 로그인해 주세요.', true);
      }
      const error = new Error(data.message || '요청을 처리하지 못했습니다. 다시 시도해 주세요.');
      error.code = data.code; error.details = data.details; error.status = response.status;
      throw error;
    }
    return data;
  }
  async function busy(button, action) {
    const original = button.textContent; button.disabled = true; button.textContent = '처리 중…';
    try { return await action(); }
    finally { button.disabled = false; button.textContent = original; }
  }

  function switchAuth(mode) {
    const register = mode === 'register';
    $('#login-form').hidden = register; $('#register-form').hidden = !register;
    $('#login-tab').classList.toggle('active', !register); $('#register-tab').classList.toggle('active', register);
    $('#auth-title').textContent = register ? '함께 쓰는 공간의 시작.' : '다시 만나 반가워요.';
    $('#auth-description').textContent = register ? '이메일 인증을 완료하면 좌석을 예약할 수 있어요.' : '학번 또는 이메일로 로그인하세요.';
    showMessage($('#auth-message'), '');
  }
  function openAuth(mode = 'login') {
    switchAuth(mode); if (!$('#auth-dialog').open) $('#auth-dialog').showModal();
  }
  function signOut(notify = true) {
    state.generation++; state.user = null; state.token = null; state.selected = null;
    state.loaded = false; state.loading = false; state.reservations = []; state.dirty = true;
    sessionStorage.removeItem('lsrs.token');
    document.querySelectorAll('dialog[open]').forEach(dialog => dialog.close());
    if (state.qrUrl) { URL.revokeObjectURL(state.qrUrl); state.qrUrl = null; }
    renderSession(); if (notify) toast('로그아웃했어요.');
  }
  function renderSession() {
    const loggedIn = Boolean(state.user), admin = state.user?.role === 'ADMIN';
    $('#welcome').hidden = loggedIn;
    $('#student-workspace').hidden = !loggedIn || admin;
    $('#admin-workspace').hidden = !loggedIn || !admin;
    $('#open-login').hidden = loggedIn; $('#logout-button').hidden = !loggedIn;
    $('#user-chip').hidden = !loggedIn; $('#my-nav').hidden = !loggedIn || admin;
    $('#user-chip').textContent = loggedIn ? `${admin ? '관리자' : state.user.studentNo} · ${state.user.email.split('@')[0]}` : '';
    $('#page-title').textContent = admin ? '공정한 이용을 위한 운영 공간.' : '필요한 시간만큼, 나에게 맞는 자리.';
    $('#page-lead').textContent = admin ? '운영 기간을 확인하고, 좌석에 부착할 QR을 관리하세요.' : '좌석을 확인하고 예약한 뒤, 도착하면 QR로 체크인하세요.';
  }
  async function afterLogin(data) {
    state.token = data.accessToken; state.user = data.user;
    sessionStorage.setItem('lsrs.token', state.token);
    if ($('#auth-dialog').open) $('#auth-dialog').close();
    renderSession(); showMessage($('#notice'), '');
    if (state.user.role === 'ADMIN') await loadAdmin();
    else { initializeTimes(); await refreshData(true); }
  }

  function initializeTimes() {
    const now = serverNow(), p = parts(now);
    let minute = Number(p.hour) * 60 + Number(p.minute);
    const exact = Number(p.minute) % 30 === 0 && new Date(now).getUTCSeconds() === 0 && new Date(now).getUTCMilliseconds() === 0;
    minute = exact ? minute : Math.ceil((minute + 0.01) / 30) * 30;
    let date = dateOf(now);
    if (minute >= 1440) { date = dateOf(now + 86400000); minute = 9 * 60; }
    $('#date').min = dateOf(now); $('#date').value = date;
    for (const [id, first, last] of [['#start-time', 0, 1410], ['#end-time', 30, 1440]]) {
      $(id).replaceChildren();
      for (let value = first; value <= last; value += 30) {
        const text = `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
        const option = element('option', '', text); option.value = text; $(id).append(option);
      }
    }
    $('#start-time').value = `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`;
    const end = Math.min(minute + 60, 1440);
    $('#end-time').value = `${String(Math.floor(end / 60)).padStart(2, '0')}:${String(end % 60).padStart(2, '0')}`;
    state.dirty = true; state.selected = null;
  }
  function reservationDateTime(date, time) {
    if (time !== '24:00') { return `${date}T${time}:00+09:00`; }
    const nextDate = new Date(`${date}T00:00:00Z`);
    nextDate.setUTCDate(nextDate.getUTCDate() + 1);
    return `${nextDate.toISOString().slice(0, 10)}T00:00:00+09:00`;
  }
  function queryFromForm() { return { date: $('#date').value, start: $('#start-time').value, end: $('#end-time').value }; }
  function duration(query) {
    const minutes = value => Number(value.split(':')[0]) * 60 + Number(value.split(':')[1]);
    return minutes(query.end) - minutes(query.start);
  }
  function markDirty() {
    state.generation++; state.loading = false; state.dirty = true; state.selected = null;
    $('#time-hint').textContent = '시간이 바뀌었어요. 좌석 조회를 눌러 최신 현황을 확인해 주세요.';
    $('#time-hint').classList.add('warning'); renderSummary();
  }
  function renderPolicy() {
    if (!state.policy) return;
    $('#policy-mode').textContent = state.policy.mode === 'EXAM' ? '시험기간 운영' : '평상시 운영';
    $('#policy-max').textContent = `${state.policy.maxMinutes / 60}시간`;
    $('#policy-unit').textContent = `${state.policy.reservationUnitMinutes}분`;
    $('#policy-grace').textContent = `${state.policy.graceMinutes}분`;
  }
  async function refreshData(force = false) {
    if (!state.user || state.user.role !== 'STUDENT' || (state.loading && !force)) return;
    const query = force ? queryFromForm() : state.query;
    if (!query) return;
    if (!query.date || !query.start || !query.end || duration(query) <= 0) {
      state.loaded = false; state.selected = null;
      $('#time-hint').textContent = '시작 시각보다 늦은 종료 시각을 선택해 주세요.';
      $('#time-hint').classList.add('warning'); renderSummary(); return;
    }
    const generation = ++state.generation; state.loading = true;
    $('#query-button').disabled = true;
    try {
      const params = new URLSearchParams(query);
      const result = await api(`/api/seats?${params}`);
      if (generation !== state.generation || !state.user) return;
      state.seats = result.seats; state.policy = result.policy; state.query = query;
      state.loaded = true; state.dirty = false;
      setServerTime(result.serverTime);
      if (state.selected) state.selected = result.seats.find(seat => seat.id === state.selected.id && seat.available) || null;
      renderPolicy(); renderSeats(); renderSummary();
      $('#time-hint').textContent = `${query.start}–${query.end} 좌석 현황이에요. 다른 이용자의 변경 사항은 자동으로 갱신돼요.`;
      $('#time-hint').classList.remove('warning');
      $('#last-updated').textContent = `${timeOf(result.serverTime)} 확인`;
      try {
        const reservations = await api('/api/me/reservations');
        if (generation === state.generation && state.user) { state.reservations = reservations; renderMine(); }
      } catch (error) {
        if (generation === state.generation && state.user) $('#my-list').replaceChildren(element('p', 'inline-error', error.message));
      }
    } catch (error) {
      if (generation !== state.generation || !state.user) return;
      state.loaded = false; state.selected = null; $('#available-count').textContent = '—';
      $('#last-updated').textContent = '조회 실패';
      const empty = element('div', 'empty-state error');
      empty.append(element('span', 'empty-symbol', '↻'), element('p', '', error.message));
      const retry = element('button', 'text-button', '다시 조회하기'); retry.type = 'button'; retry.addEventListener('click', () => refreshData(true));
      empty.append(retry); $('#seat-grid').replaceChildren(empty); renderSummary();
    } finally {
      if (generation === state.generation) { state.loading = false; if (state.user?.role === 'STUDENT') renderSummary(); }
      $('#query-button').disabled = false;
    }
  }
  function renderSeats() {
    const focusedCode = document.activeElement?.dataset?.seatCode;
    const host = $('#seat-grid'); host.replaceChildren();
    const visible = state.seats.filter(seat => state.zone === 'ALL' || seat.zone === state.zone);
    $('#available-count').textContent = visible.filter(seat => seat.available).length;
    $('#total-count').textContent = visible.length;
    for (const zone of ['QUIET', 'OPEN']) {
      const rows = visible.filter(seat => seat.zone === zone); if (!rows.length) continue;
      const heading = element('div', 'zone-heading'); heading.append(element('h3', '', zoneNames[zone]), element('span', '', zone === 'QUIET' ? '조용히 집중하는 공간' : '개인 학습을 위한 열린 공간'));
      const grid = element('div', 'seats-grid');
      for (const seat of rows) {
        const button = element('button', `seat state-${seat.displayStatus}`);
        button.type = 'button'; button.dataset.seatCode = seat.code;
        button.setAttribute('aria-label', `${seat.code} · ${names[seat.displayStatus]}`);
        button.title = `${seat.code} · ${names[seat.displayStatus]}`;
        button.disabled = !seat.available; button.classList.toggle('selected', state.selected?.id === seat.id);
        button.setAttribute('aria-pressed', String(state.selected?.id === seat.id));
        button.append(element('span', 'seat-code', seat.code));
        button.addEventListener('click', () => {
          if (!state.loaded || state.dirty) { toast('변경한 시간의 좌석 현황을 먼저 조회해 주세요.', true); return; }
          state.selected = state.selected?.id === seat.id ? null : seat; renderSeats(); renderSummary();
        });
        grid.append(button);
      }
      host.append(heading, grid);
    }
    if (!visible.some(seat => seat.available)) host.append(element('p', 'field-hint', '선택한 시간대에 예약 가능한 좌석이 없어요. 다른 시간대를 확인해 주세요.'));
    if (focusedCode) host.querySelector(`[data-seat-code="${focusedCode}"]`)?.focus({ preventScroll: true });
  }
  function renderSummary() {
    const seat = $('#selected-seat'); seat.replaceChildren(document.createTextNode(state.selected?.code || '—'));
    seat.append(element('small', '', state.selected ? zoneNames[state.selected.zone] : '예약 가능한 좌석을 선택해 주세요'));
    const query = state.query;
    $('#summary-date').textContent = query ? dateLabel(`${query.date}T12:00:00+09:00`) : '—';
    $('#summary-time').textContent = query ? `${query.start} – ${query.end}` : '—';
    const minutes = query ? duration(query) : 0;
    $('#summary-duration').textContent = minutes > 0 ? `${Math.floor(minutes / 60) ? `${Math.floor(minutes / 60)}시간 ` : ''}${minutes % 60 ? `${minutes % 60}분` : ''}`.trim() : '—';
    let error = '';
    if (state.dirty) error = '선택한 시간의 좌석 현황을 먼저 조회해 주세요.';
    else if (state.policy && minutes > state.policy.maxMinutes) error = `현재 운영 정책에서는 최대 ${state.policy.maxMinutes / 60}시간까지 예약할 수 있어요.`;
    else if (state.user?.restrictedUntil && Date.parse(state.user.restrictedUntil) > serverNow()) error = `${dateLabel(state.user.restrictedUntil)} ${timeOf(state.user.restrictedUntil)}까지 예약이 제한되어 있어요.`;
    showMessage($('#booking-error'), error);
    $('#reserve-button').disabled = Boolean(error || !state.loaded || !state.selected || state.loading);
  }
  function renderMine() {
    const list = $('#my-list'); list.replaceChildren();
    const active = state.reservations.filter(reservation => ['RESERVED', 'CHECKED_IN'].includes(reservation.status));
    $('#my-count').textContent = active.length;
    if (!active.length) list.append(element('p', 'muted', '현재 예약한 좌석이 없어요. 원하는 좌석을 선택해 보세요.'));
    for (const reservation of active) {
      const card = element('article', 'reservation-card'); card.dataset.reservationId = reservation.id;
      const header = element('div', 'reservation-card-header'); header.append(element('strong', '', reservation.seatCode),
        element('span', reservation.status === 'CHECKED_IN' ? 'status-tag in-use' : 'status-tag', reservation.status === 'CHECKED_IN' ? '이용 중' : '입실 대기'));
      card.append(header, element('p', '', `${dateLabel(reservation.startAt)} · ${zoneNames[reservation.zone]}`),
        element('p', '', `${timeOf(reservation.startAt)} – ${reservationEndTime(reservation)}`));
      if (reservation.status === 'RESERVED') {
        card.append(element('p', 'deadline', `${timeOf(reservation.checkInDeadlineAt)}까지 체크인해 주세요`));
        const button = element('button', 'button button-secondary button-full', 'QR 체크인'); button.type = 'button';
        button.addEventListener('click', () => openCheckIn(reservation)); card.append(button);
        const cancelButton = element('button', 'button button-subtle button-full', '예약 취소');
        cancelButton.type = 'button';
        cancelButton.addEventListener('click', () => confirmCancellation(reservation));
        card.append(cancelButton);
      } else {
        const button = element('button', 'button button-secondary button-full', '퇴실하고 좌석 반환'); button.type = 'button';
        button.addEventListener('click', () => confirmExit(reservation)); card.append(button);
      }
      list.append(card);
    }
    const completed = state.reservations.filter(reservation => reservation.status === 'COMPLETED');
    if (completed.length) list.append(element('p', 'field-hint', `이용 완료 ${completed.length}건 · 좌석을 반환해 주셔서 감사해요.`));
  }

  function confirmation(title, text, label, action) {
    $('#confirm-title').textContent = title; $('#confirm-text').textContent = text;
    $('#confirm-action').textContent = label; showMessage($('#confirm-error'), '');
    state.confirming = action; $('#confirm-dialog').showModal();
  }
  function confirmReservation() {
    if (!state.selected || !state.loaded || state.dirty) return;
    const seat = state.selected, query = { ...state.query }, version = state.policy.versionNo;
    confirmation(`${seat.code} 좌석을 예약할까요?`, `${zoneNames[seat.zone]}\n${dateLabel(`${query.date}T12:00:00+09:00`)} ${query.start} – ${query.end}\n도착 후 예약 시작 시각부터 ${state.policy.graceMinutes}분 안에 체크인해 주세요.`, '예약 확정', async () => {
      await api('/api/reservations', { method: 'POST', body: { seatId: seat.id,
        startAt: reservationDateTime(query.date, query.start), endAt: reservationDateTime(query.date, query.end), policyVersion: version } });
      $('#confirm-dialog').close(); state.selected = null;
      toast(`${seat.code} 좌석 예약이 완료되었어요.`); await refreshData(true);
    });
  }
  function confirmExit(reservation) {
    confirmation('이용을 마치셨나요?', `${reservation.seatCode} 좌석의 남은 시간을 반환합니다.\n퇴실 후 다른 학생이 이 좌석을 예약할 수 있어요.`, '퇴실 확정', async () => {
      await api(`/api/reservations/${reservation.id}/check-outs`, { method: 'POST' });
      $('#confirm-dialog').close(); toast(`${reservation.seatCode} 좌석 반환을 완료했어요.`); window.location.reload();
    });
  }

  function confirmCancellation(reservation) {
    confirmation('예약을 취소할까요?', `${reservation.seatCode} 좌석 예약을 취소합니다.\n취소하면 다른 학생이 이 좌석을 예약할 수 있어요.`, '예약 취소', async () => {
      const cancelled = await api(
        `/api/reservations/${reservation.id}/cancellations`,
        { method: 'POST' }
      );

      if (cancelled.id !== reservation.id || cancelled.status !== 'CANCELLED') {
        throw new Error(
          '서버에서 취소 완료 상태를 확인하지 못했어요. 내 이용 현황을 다시 확인해 주세요.'
        );
      }

      // 취소 전 시작된 조회 결과가 이전 화면을 복원하지 않도록 처리
      state.generation++;
      state.selected = null;

      // 서버가 확인한 취소 결과를 이용 현황에 바로 반영
      state.reservations = state.reservations.map(item =>
        item.id === cancelled.id ? cancelled : item
      );
      renderMine();
      $('#confirm-dialog').close();

      // 좌석 상태는 서버에서 다시 조회
      await refreshData(true);

      toast(state.loaded
        ? '예약이 취소되었습니다.'
        : '예약이 취소되었습니다. 좌석 현황을 다시 조회해 주세요.');
    });
  }

  function stopCamera() {
    state.cameraEpoch++; state.scanning = false;
    if (state.stream) state.stream.getTracks().forEach(track => track.stop());
    state.stream = null; $('#camera-video').srcObject = null; $('#camera-video').hidden = true;
    $('#camera-placeholder').hidden = false;
  }
  function openCheckIn(reservation) {
    stopCamera(); state.checkin = reservation; state.scanBusy = false; $('#qr-file').value = '';
    $('#checkin-title').textContent = `${reservation.seatCode} 좌석에 도착하셨나요?`;
    $('#checkin-description').textContent = `${timeOf(reservation.startAt)} – ${reservationEndTime(reservation)} · ${timeOf(reservation.checkInDeadlineAt)}까지 체크인`;
    showMessage($('#checkin-message'), ''); $('#checkin-dialog').showModal();
  }
  async function submitQr(payload) {
    if (state.scanBusy || !state.checkin || !$('#checkin-dialog').open) return;
    const decoded = payload.split('|');
    if (decoded.length !== 3 || decoded[0] !== 'LSRS1') { showMessage($('#checkin-message'), '이 시스템의 좌석 QR이 아닙니다. 좌석에 부착된 QR을 확인해 주세요.'); return; }
    if (decoded[1] !== state.checkin.seatCode) { showMessage($('#checkin-message'), '예약한 좌석과 다른 QR입니다. 좌석 번호를 확인해 주세요.'); return; }
    state.scanBusy = true; stopCamera();
    try {
      await api(`/api/reservations/${state.checkin.id}/check-ins`, { method: 'POST', body: { qrToken: decoded[2] } });
      $('#checkin-dialog').close(); toast('체크인이 완료되었어요. 편안한 학습 시간 보내세요.'); await refreshData(true);
    } catch (error) { showMessage($('#checkin-message'), error.message); }
    finally { state.scanBusy = false; }
  }
  async function startCamera() {
    stopCamera(); showMessage($('#checkin-message'), '');
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      showMessage($('#checkin-message'), '이 연결에서는 카메라를 사용할 수 없어요. localhost 또는 HTTPS로 접속하거나 QR 이미지를 선택해 주세요.'); return;
    }
    const epoch = state.cameraEpoch;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 640 } }, audio: false });
      if (epoch !== state.cameraEpoch || !$('#checkin-dialog').open) { stream.getTracks().forEach(track => track.stop()); return; }
      state.stream = stream; state.scanning = true;
      const video = $('#camera-video'); video.srcObject = stream; video.hidden = false; $('#camera-placeholder').hidden = true;
      await video.play(); let previous = 0;
      const frame = async timestamp => {
        if (!state.scanning || !$('#checkin-dialog').open) return;
        if (video.readyState >= 2 && timestamp - previous > 220 && !state.scanBusy) {
          previous = timestamp;
          const canvas = $('#scan-canvas'); canvas.width = 640; canvas.height = Math.round(video.videoHeight / video.videoWidth * 640);
          const context = canvas.getContext('2d', { willReadFrequently: true }); context.drawImage(video, 0, 0, canvas.width, canvas.height);
          const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
          const qr = window.jsQR(pixels.data, pixels.width, pixels.height, { inversionAttempts: 'dontInvert' });
          if (qr) await submitQr(qr.data);
        }
        if (state.scanning) requestAnimationFrame(frame);
      };
      requestAnimationFrame(frame);
    } catch (error) {
      stopCamera();
      showMessage($('#checkin-message'), error.name === 'NotAllowedError' ? '카메라 권한이 거부되었어요. 권한을 허용하고 다시 시도하거나 QR 이미지를 선택해 주세요.' : '카메라에 연결하지 못했어요. 다른 앱에서 사용 중인지 확인하거나 QR 이미지를 선택해 주세요.');
    }
  }
  async function scanFile(file) {
    if (!file) return; stopCamera(); showMessage($('#checkin-message'), '');
    if (file.size > 5 * 1024 * 1024) { showMessage($('#checkin-message'), '5MB 이하의 QR 이미지를 선택해 주세요.'); return; }
    try {
      const bitmap = await createImageBitmap(file); const canvas = $('#scan-canvas');
      const scale = Math.min(1, 2000 / Math.max(bitmap.width, bitmap.height));
      canvas.width = Math.round(bitmap.width * scale); canvas.height = Math.round(bitmap.height * scale);
      const context = canvas.getContext('2d', { willReadFrequently: true }); context.drawImage(bitmap, 0, 0, canvas.width, canvas.height); bitmap.close();
      const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
      const qr = window.jsQR(pixels.data, pixels.width, pixels.height);
      if (!qr) { showMessage($('#checkin-message'), '이미지에서 QR을 찾지 못했어요. QR 전체가 선명하게 보이는 이미지를 선택해 주세요.'); return; }
      await submitQr(qr.data);
    } catch (error) { showMessage($('#checkin-message'), '이미지를 읽지 못했어요. PNG·JPEG·WebP 파일을 다시 선택해 주세요.'); }
    finally { $('#qr-file').value = ''; }
  }

  async function loadAdmin() {
    try {
      state.policy = await api('/api/admin/policies'); $('#admin-mode').value = state.policy.mode;
      state.adminSeats = await api('/api/admin/seats');
      $('#qr-seat-select').replaceChildren(...state.adminSeats.map(seat => {
        const option = element('option', '', `${seat.code} · ${zoneNames[seat.zone]}`); option.value = seat.id; return option;
      }));
      const example = state.adminSeats.find(seat => seat.code === 'C03'); if (example) $('#qr-seat-select').value = example.id;
      await loadQr();
    } catch (error) { showMessage($('#notice'), error.message); }
  }
  async function loadQr() {
    const generation = ++state.qrGeneration;
    const id = $('#qr-seat-select').value;
    const seat = state.adminSeats?.find(candidate => candidate.id === Number(id)); if (!seat) return;
    try {
      const response = await fetch(`/api/admin/seats/${id}/qr.png`, { headers: { Authorization: `Bearer ${state.token}` }, cache: 'no-store' });
      if (!response.ok) throw new Error('QR 이미지를 불러오지 못했어요. 다시 로그인하거나 좌석을 다시 선택해 주세요.');
      const blob = await response.blob(); if (generation !== state.qrGeneration || !state.user) return;
      if (state.qrUrl) URL.revokeObjectURL(state.qrUrl);
      state.qrUrl = URL.createObjectURL(blob); state.qrSeat = seat;
      const image = element('img'); image.alt = `${seat.code} 좌석 입실 QR`; image.src = state.qrUrl;
      $('#qr-preview').replaceChildren(element('strong', '', seat.code), image, element('p', 'field-hint', '이 좌석을 예약한 뒤 QR을 스캔해 주세요.'));
    } catch (error) { $('#qr-preview').replaceChildren(element('p', 'inline-error', error.message)); }
  }

  $('#open-login').addEventListener('click', () => openAuth());
  $('#welcome-login').addEventListener('click', () => openAuth());
  $('#welcome-register').addEventListener('click', () => openAuth('register'));
  $('#login-tab').addEventListener('click', () => switchAuth('login'));
  $('#register-tab').addEventListener('click', () => switchAuth('register'));
  $('#logout-button').addEventListener('click', () => signOut());
  document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => $(`#${button.dataset.close}`).close()));
  $('#register-role').addEventListener('change', () => { $('#register-student-no').required = $('#register-role').value === 'STUDENT'; });
  $('#login-form').addEventListener('submit', async event => {
    event.preventDefault(); showMessage($('#auth-message'), '');
    await busy(event.submitter, async () => {
      try { await afterLogin(await api('/api/auth/tokens', { method: 'POST', auth: false, body: {
        identifier: $('#login-identifier').value, password: $('#login-password').value } })); $('#login-password').value = ''; }
      catch (error) { showMessage($('#auth-message'), error.message); }
    });
  });
  $('#register-form').addEventListener('submit', async event => {
    event.preventDefault(); showMessage($('#auth-message'), '');
    await busy(event.submitter, async () => {
      try {
        const result = await api('/api/users', { method: 'POST', auth: false, body: { studentNo: $('#register-student-no').value,
          email: $('#register-email').value, password: $('#register-password').value, requestedRole: $('#register-role').value } });
        $('#login-identifier').value = $('#register-email').value; $('#register-password').value = '';
        showMessage($('#auth-message'), result.message, result.deliveryStatus === 'FAILED');
      } catch (error) { showMessage($('#auth-message'), error.message); }
    });
  });
  $('#resend-button').addEventListener('click', async event => {
    const email = $('#register-form').hidden ? $('#login-identifier').value : $('#register-email').value;
    if (!email.includes('@')) { showMessage($('#auth-message'), '가입한 이메일 주소를 입력한 후 다시 요청해 주세요.'); return; }
    await busy(event.currentTarget, async () => {
      try { const result = await api('/api/email-verifications/resend', { method: 'POST', auth: false, body: { email } }); showMessage($('#auth-message'), result.message, false); }
      catch (error) { showMessage($('#auth-message'), error.message); }
    });
  });
  for (const [id, identifier] of [['#demo-student', 'student@example.test'], ['#demo-admin', 'admin@example.test']]) {
    $(id).addEventListener('click', async event => {
      await busy(event.currentTarget, async () => {
        try { await afterLogin(await api('/api/auth/tokens', { method: 'POST', auth: false, body: { identifier, password: 'Demo1234!' } })); }
        catch (error) { showMessage($('#notice'), error.message); }
      });
    });
  }
  $('#time-form').addEventListener('submit', event => { event.preventDefault(); refreshData(true); });
  $('#time-form').addEventListener('change', markDirty);
  $('#refresh-button').addEventListener('click', () => refreshData(true));
  document.querySelectorAll('[data-zone]').forEach(button => button.addEventListener('click', () => {
    state.zone = button.dataset.zone;
    document.querySelectorAll('[data-zone]').forEach(tab => { tab.classList.toggle('active', tab === button); tab.setAttribute('aria-pressed', String(tab === button)); });
    if (state.loaded) renderSeats();
  }));
  $('#reserve-button').addEventListener('click', confirmReservation);
  $('#confirm-action').addEventListener('click', async event => {
    await busy(event.currentTarget, async () => { try { await state.confirming?.(); } catch (error) { showMessage($('#confirm-error'), error.message); } });
  });
  $('#start-camera').addEventListener('click', startCamera);
  $('#qr-file').addEventListener('change', event => scanFile(event.target.files[0]));
  $('#checkin-dialog').addEventListener('close', () => { stopCamera(); state.checkin = null; });
  window.addEventListener('beforeunload', stopCamera);
  $('#policy-form').addEventListener('submit', async event => {
    event.preventDefault();
    await busy(event.submitter, async () => {
      try {
        state.policy = await api('/api/admin/policies', { method: 'PUT', body: { mode: $('#admin-mode').value, versionNo: state.policy.versionNo } });
        $('#policy-message').textContent = `${state.policy.mode === 'EXAM' ? '시험기간' : '평상시'} 정책을 적용했어요. 새 예약은 최대 ${state.policy.maxMinutes / 60}시간까지 가능해요.`;
        toast('운영 기간을 변경했어요.');
      } catch (error) { $('#policy-message').textContent = error.message; if (error.code === 'POLICY_CHANGED') state.policy = await api('/api/admin/policies'); }
    });
  });
  $('#qr-seat-select').addEventListener('change', loadQr);
  $('#download-qr').addEventListener('click', () => {
    if (!state.qrUrl || !state.qrSeat) return;
    const anchor = element('a'); anchor.href = state.qrUrl; anchor.download = `LSRS-${state.qrSeat.code}-QR.png`; anchor.click();
  });
  $('#print-qr').addEventListener('click', () => { if (state.qrUrl) window.print(); });

  async function initialize() {
    try {
      state.info = await api('/api/app-info', { auth: false }); state.info.receivedAt = Date.now();
      setServerTime(state.info.serverTime); $('#demo-label').hidden = !state.info.demo; $('#demo-welcome').hidden = !state.info.demo;
      document.querySelectorAll('[data-local-only]').forEach(node => { node.hidden = !state.info.localMailbox; });
      if (state.token) {
        try { state.user = await api('/api/me'); renderSession(); if (state.user.role === 'ADMIN') await loadAdmin(); else { initializeTimes(); await refreshData(true); } }
        catch (error) { signOut(false); }
      }
      setInterval(() => { if (state.user?.role === 'STUDENT' && !document.hidden && !state.dirty && !$('#confirm-dialog').open) refreshData(); }, Math.max(2, state.info.pollingSeconds) * 1000);
    } catch (error) { $('#server-clock').textContent = '연결 확인 필요'; showMessage($('#notice'), error.message); }
  }
  initialize();
})();
