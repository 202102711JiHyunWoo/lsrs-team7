'use strict';
// Run against a disposable demo database. No existing user database is touched.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { spawn } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const base = `http://localhost:${process.env.LSRS_E2E_PORT || 18088}`;
const checks = [], pageErrors = [];
let server, browser, temp, log;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(fn, label, timeout = 12000) {
  const start = Date.now();
  while (Date.now() - start < timeout) { if (await fn()) return; await sleep(80); }
  throw new Error(`Timed out: ${label}`);
}
async function check(name, action) { await action(); checks.push(name); console.log(`PASS ${name}`); }
async function api(endpoint, body, token, method) {
  const response = await fetch(base + endpoint, { method: method || (body ? 'POST' : 'GET'),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined });
  const text = await response.text();
  return { status: response.status, data: text ? JSON.parse(text) : null };
}
function watch(page) { page.on('pageerror', error => pageErrors.push(error.message)); return page; }
async function login(page, identifier, password = 'Demo1234!') {
  await page.goto(base);
  await page.locator('#open-login').click();
  await page.locator('#login-identifier').fill(identifier);
  await page.locator('#login-password').fill(password);
  await page.locator('#login-form button[type=submit]').click();
  await page.locator('[data-seat-code="C03"]').waitFor();
}
async function book(page, code) {
  await page.locator(`[data-seat-code="${code}"]`).click();
  await until(() => page.locator('#reserve-button').isEnabled(), 'booking enabled');
  await page.locator('#reserve-button').click();
  await page.locator('#confirm-action').click();
  await page.locator('#my-list .reservation-card').waitFor();
  await until(async () => !(await page.locator('#confirm-dialog').evaluate(el => el.open)), 'booking confirmed');
}
function launch(extra = []) {
  return chromium.launch({ headless: true,
    ...(process.env.LSRS_BROWSER_EXECUTABLE ? { executablePath: process.env.LSRS_BROWSER_EXECUTABLE } : {}),
    args: ['--no-sandbox', ...extra] });
}
(async () => {
  temp = await fs.mkdtemp(path.join(os.tmpdir(), 'lsrs-e2e-'));
  await fs.mkdir(path.join(root, 'reports'), { recursive: true });
  log = require('node:fs').openSync(path.join(root, 'reports/e2e-server.log'), 'w');
  const buildJar = path.join(root, 'build/libs/lsrs-sprint1.jar');
  const jar = require('node:fs').existsSync(buildJar) ? buildJar : path.join(root, 'dist/lsrs-sprint1.jar');
  server = spawn(process.env.LSRS_JAVA || 'java', ['-jar', jar, '--spring.profiles.active=demo',
    `--server.port=${new URL(base).port}`, `--app.public-base-url=${base}`,
    `--spring.datasource.url=jdbc:h2:file:${path.join(temp, 'database')};DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000`,
    `--app.mail.outbox-directory=${path.join(temp, 'mailbox')}`], { cwd: temp, stdio: ['ignore', log, log] });
  server.on('error', e => console.error(e));
  await until(async () => { try { return (await fetch(base + '/api/app-info')).ok; } catch { return false; } }, 'server startup', 60000);
  let adminLogin;
  await until(async () => { adminLogin = await api('/api/auth/tokens', { identifier: 'admin@example.test', password: 'Demo1234!' }); return adminLogin.status === 200; }, 'demo initialization committed', 30000);
  const admin = adminLogin.data;
  const seatResponse = await api('/api/admin/seats', null, admin.accessToken);
  assert.equal(seatResponse.status, 200, JSON.stringify(seatResponse));
  const seats = seatResponse.data;
  const seat = code => seats.find(item => item.code === code);
  for (const code of ['C03', 'C04']) {
    const response = await fetch(`${base}/api/admin/seats/${seat(code).id}/qr.png`, { headers: { Authorization: `Bearer ${admin.accessToken}` } });
    assert.equal(response.status, 200);
    await fs.writeFile(path.join(temp, `${code}.png`), Buffer.from(await response.arrayBuffer()));
    // These are reproducible assets for the fixed-key demo profile only.
    await fs.copyFile(path.join(temp, `${code}.png`), path.join(root, `demo/LSRS-${code}-QR.png`));
  }
  browser = await launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
  const page = watch(await context.newPage());
  await check('desktop landing and local assets', async () => {
    const external = []; page.on('request', request => { if (!request.url().startsWith(base) && /^https?:/.test(request.url())) external.push(request.url()); });
    await page.goto(base); await page.locator('#demo-student').waitFor();
    assert.match(await page.title(), /LSRS/); assert.deepEqual(external, []);
    if (process.env.LSRS_QA_SHOTS) await page.screenshot({ path: path.join(process.env.LSRS_QA_SHOTS, 'welcome.png'), fullPage: true });
  });
  await check('signup, unverified login rejection, local email verification', async () => {
    await page.locator('#welcome-register').click();
    await page.locator('#register-student-no').fill('20269999');
    await page.locator('#register-email').fill('browser@example.test');
    await page.locator('#register-password').fill('Browser123!');
    await page.locator('#register-form button[type=submit]').click();
    await until(async () => /인증/.test(await page.locator('#auth-message').innerText()), 'signup response');
    assert.equal((await api('/api/auth/tokens', { identifier: 'browser@example.test', password: 'Browser123!' })).status, 403);
    const mailbox = watch(await context.newPage()); await mailbox.goto(base + '/dev/mailbox');
    await mailbox.locator('#mail-email').fill('browser@example.test');
    await mailbox.locator('button[type=submit]').click();
    const popup = context.waitForEvent('page');
    await mailbox.locator('a[href*="verify-email?token="]').first().click();
    const verification = watch(await popup);
    await until(async () => /완료/.test(await verification.locator('body').innerText()), 'email verification page');
    await verification.close(); await mailbox.close();
    await page.locator('[data-close="auth-dialog"]').click();
    await login(page, 'browser@example.test', 'Browser123!');
  });
  await check('48-seat grid, four states, refresh preserves selectable booking', async () => {
    assert.equal(await page.locator('.seat').count(), 48);
    assert.equal(await page.locator('[data-seat-code="A02"]').isDisabled(), true);
    assert.equal(await page.locator('[data-seat-code="A05"]').isDisabled(), true);
    await page.locator('[data-seat-code="C03"]').click();
    await page.locator('#refresh-button').click();
    await until(() => page.locator('#reserve-button').isEnabled(), 'selection after refresh');
    if (process.env.LSRS_QA_SHOTS) await page.screenshot({ path: path.join(process.env.LSRS_QA_SHOTS, 'student.png'), fullPage: true });
    await page.locator('[data-seat-code="C03"]').click();
  });
  await check('changed time requires a fresh query', async () => {
    await page.locator('[data-seat-code="C03"]').click();
    await page.locator('#end-time').selectOption('11:30');
    assert.equal(await page.locator('#reserve-button').isDisabled(), true);
    await page.locator('#end-time').selectOption('11:00');
    await page.locator('#query-button').click();
    await until(async () => (await page.locator('#booking-error').isHidden()), 'new query');
  });
  let reservation;
  await check('booking confirmation and server ownership/overlap enforcement', async () => {
    await book(page, 'C03');
    const jwt = await page.evaluate(() => sessionStorage.getItem('lsrs.token'));
    reservation = (await api('/api/me/reservations', null, jwt)).data[0];
    assert.equal(reservation.status, 'RESERVED');
    assert.equal((await api('/api/admin/seats', null, jwt)).status, 403);
    const competitor = (await api('/api/auth/tokens', { identifier: 'student@example.test', password: 'Demo1234!' })).data;
    const conflict = await api('/api/reservations', { seatId: seat('C03').id, startAt: '2026-11-14T10:00:00+09:00', endAt: '2026-11-14T11:00:00+09:00', policyVersion: reservation.policyVersion }, competitor.accessToken);
    assert.equal(conflict.status, 409);
  });
  await check('camera denial, wrong QR and correct QR image check-in', async () => {
    await page.getByRole('button', { name: 'QR 체크인', exact: true }).click();
    // Deterministic permission denial exercises the actual UI error handler.
    await page.evaluate(() => { navigator.mediaDevices.getUserMedia = async () => { throw new DOMException('Denied', 'NotAllowedError'); }; });
    await page.locator('#start-camera').click();
    await until(async () => /권한이 거부/.test(await page.locator('#checkin-message').innerText()), 'camera denial');
    await page.locator('#qr-file').setInputFiles(path.join(temp, 'C04.png'));
    await until(async () => /다른 QR/.test(await page.locator('#checkin-message').innerText()), 'wrong QR');
    const jwt = await page.evaluate(() => sessionStorage.getItem('lsrs.token'));
    assert.equal((await api(`/api/reservations/${reservation.id}`, null, jwt)).data.status, 'RESERVED');
    await page.locator('#qr-file').setInputFiles(path.join(temp, 'C03.png'));
    await page.getByRole('button', { name: '퇴실하고 좌석 반환', exact: true }).waitFor();
    assert.equal((await api(`/api/reservations/${reservation.id}`, null, jwt)).data.status, 'CHECKED_IN');
  });
  await check('early checkout releases the seat', async () => {
    await page.getByRole('button', { name: '퇴실하고 좌석 반환', exact: true }).click();
    await page.locator('#confirm-action').click();
    await until(async () => await page.locator('#my-list .reservation-card').count() === 0, 'checkout');
    await until(() => page.locator('[data-seat-code="C03"]').isEnabled(), 'seat released');
  });
  await check('fully booked grid shows zero availability and an empty-availability explanation', async () => {
    await page.route('**/api/seats?*', async route => {
      const response = await route.fetch(); const data = await response.json();
      data.availableCount = 0;
      data.seats = data.seats.map(seat => ({ ...seat, available: false, displayStatus: 'RESERVED' }));
      await route.fulfill({ response, json: data });
    });
    await page.locator('#refresh-button').click();
    await until(async () => (await page.locator('#available-count').innerText()) === '0', 'fully booked display');
    assert.match(await page.locator('#seat-grid').innerText(), /예약 가능한 좌석이 없어요/);
    assert.equal(await page.locator('.seat:enabled').count(), 0);
    assert.equal(await page.locator('#reserve-button').isDisabled(), true);
    await page.unroute('**/api/seats?*');
    await page.locator('#refresh-button').click();
    await until(() => page.locator('[data-seat-code="C03"]').isEnabled(), 'availability restored');
  });
  await check('failed seat lookup never displays false availability', async () => {
    await page.route('**/api/seats?*', route => route.abort('failed'));
    await page.locator('#refresh-button').click();
    await until(async () => (await page.locator('#last-updated').innerText()) === '조회 실패', 'failed query');
    assert.equal(await page.locator('#available-count').innerText(), '—');
    assert.equal(await page.locator('#reserve-button').isDisabled(), true);
    assert.equal(await page.locator('.seat').count(), 0);
    await page.unroute('**/api/seats?*');
    await page.locator('#refresh-button').click(); await page.locator('[data-seat-code="C03"]').waitFor();
  });
  await check('administrator mode update reaches student policy display', async () => {
    const adminContext = await browser.newContext(); const adminPage = watch(await adminContext.newPage());
    await adminPage.goto(base); await adminPage.locator('#demo-admin').click();
    await adminPage.locator('#qr-preview img').waitFor();
    await adminPage.locator('#admin-mode').selectOption('EXAM');
    await adminPage.locator('#policy-form button[type=submit]').click();
    await until(async () => /최대 3시간/.test(await adminPage.locator('#policy-message').innerText()), 'mode saved');
    await page.locator('#refresh-button').click();
    await until(async () => (await page.locator('#policy-max').innerText()) === '3시간', 'policy refreshed');
    await adminPage.locator('#admin-mode').selectOption('NORMAL');
    await adminPage.locator('#policy-form button[type=submit]').click();
    await until(async () => /최대 4시간/.test(await adminPage.locator('#policy-message').innerText()), 'mode restored');
    await adminContext.close();
  });
  await check('390px mobile layout, dialog and keyboard focus', async () => {
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.locator('#logout-button').click(); await page.locator('#open-login').click();
    const box = await page.locator('#auth-dialog').boundingBox(); assert.ok(box.x >= 0 && box.x + box.width <= 391);
    await page.locator('#login-identifier').focus(); await page.keyboard.press('Tab');
    assert.equal(await page.locator('#login-password').evaluate(el => el === document.activeElement), true);
    if (process.env.LSRS_QA_SHOTS) await page.screenshot({ path: path.join(process.env.LSRS_QA_SHOTS, 'mobile.png'), fullPage: true });
  });
  // A real getUserMedia -> video -> canvas -> jsQR pipeline, with deterministic video input.
  const videoPath = path.join(temp, 'qr-camera.y4m');
  const gray = await page.evaluate(async data => {
    const image = new Image(); image.src = 'data:image/png;base64,' + data; await image.decode();
    const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 480;
    const ctx = canvas.getContext('2d'); ctx.fillStyle = 'white'; ctx.fillRect(0, 0, 640, 480); ctx.drawImage(image, 140, 60, 360, 360);
    const pixels = ctx.getImageData(0, 0, 640, 480).data;
    return Array.from({ length: 640 * 480 }, (_, i) => pixels[i * 4]);
  }, (await fs.readFile(path.join(temp, 'C03.png'))).toString('base64'));
  const y = Buffer.from(gray.map(v => Math.round(16 + v * 219 / 255)));
  const uv = Buffer.alloc(640 * 480 / 2, 128);
  const frame = Buffer.concat([Buffer.from('FRAME\n'), y, uv]);
  await fs.writeFile(videoPath, Buffer.concat([Buffer.from('YUV4MPEG2 W640 H480 F10:1 Ip A1:1 C420jpeg\n'), ...Array(10).fill(frame)]));
  await browser.close();
  browser = await launch(['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', `--use-file-for-fake-video-capture=${videoPath}`]);
  await check('live camera pipeline decodes QR and stops camera after check-in', async () => {
    const cameraPage = watch(await browser.newPage()); await login(cameraPage, 'student@example.test');
    await book(cameraPage, 'C03'); await cameraPage.getByRole('button', { name: 'QR 체크인', exact: true }).click();
    await cameraPage.locator('#start-camera').click();
    await cameraPage.getByRole('button', { name: '퇴실하고 좌석 반환', exact: true }).waitFor({ timeout: 20000 });
    assert.equal(await cameraPage.locator('#camera-video').evaluate(el => el.srcObject), null);
    assert.equal(await cameraPage.locator('#checkin-dialog').evaluate(el => el.open), false);
  });
  await check('no uncaught JavaScript errors', async () => assert.deepEqual(pageErrors, []));
  await fs.writeFile(path.join(root, 'reports/e2e-results.json'), JSON.stringify({ passed: checks.length, failed: 0, checks, pageErrors, viewport: ['1440x1100', '390x844'], camera: 'Chromium synthetic Y4M input; physical camera not tested' }, null, 2));
  console.log(`E2E ${checks.length} checks passed`);
})().catch(async error => {
  console.error(error);
  await fs.mkdir(path.join(root, 'reports'), { recursive: true });
  await fs.writeFile(path.join(root, 'reports/e2e-results.json'), JSON.stringify({ passed: checks.length, failed: 1, checks, error: error.stack, pageErrors }, null, 2));
  process.exitCode = 1;
}).finally(async () => {
  if (browser) await browser.close();
  if (server && server.exitCode === null) { server.kill('SIGTERM'); await Promise.race([new Promise(resolve => server.once('exit', resolve)), sleep(15000)]); }
  if (temp) await fs.rm(temp, { recursive: true, force: true });
});
