"""Python 3 standard-library smoke checks: real SMTP protocol, file DB restart, HTTP.
Always uses temporary data. No email leaves the local fake SMTP receiver.
Usage: python scripts/runtime_checks.py  (build or bundled JAR + Java 21 required)
"""
from pathlib import Path
import datetime as dt
import email, email.policy
import json, os, re, shutil, socketserver, statistics, subprocess, tempfile, threading, time
import urllib.request, urllib.error

ROOT = Path(__file__).resolve().parent.parent
BASE = 'http://localhost:' + os.environ.get('LSRS_RUNTIME_PORT', '18089')
REPORT = ROOT / 'reports'
REPORT.mkdir(exist_ok=True)
CHECKS = []
MESSAGES = []
REJECT_MAIL = False

class SMTPHandler(socketserver.StreamRequestHandler):
    def handle(self):
        def reply(value):
            self.wfile.write((value + '\r\n').encode()); self.wfile.flush()
        reply('220 LSRS test SMTP')
        while line := self.rfile.readline():
            command = line.decode(errors='replace').strip()
            if command.upper().startswith(('EHLO', 'HELO')): reply('250 localhost')
            elif command.upper().startswith(('MAIL FROM:', 'RCPT TO:', 'RSET', 'NOOP')): reply('250 OK')
            elif command.upper() == 'DATA':
                reply('354 End with dot')
                data = []
                while (part := self.rfile.readline()) not in (b'.\r\n', b''):
                    data.append(part[1:] if part.startswith(b'..') else part)
                if REJECT_MAIL: reply('451 Simulated delivery failure')
                else: MESSAGES.append(b''.join(data)); reply('250 queued locally')
            elif command.upper() == 'QUIT': reply('221 Bye'); break
            else: reply('502 Unsupported')

class SMTPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

def request(route, body=None, token=None, method=None, binary=False):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + route, data=json.dumps(body).encode() if body is not None else None,
                                 headers=headers, method=method)
    try: response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error: response = error
    raw = response.read()
    return response.code, raw if binary else json.loads(raw) if raw else None

def check(name, assertion):
    assertion(); CHECKS.append(name); print('PASS', name, flush=True)

def expect(condition, message='Assertion failed'):
    assert condition, message

def latest_token(recipient):
    for raw in reversed(MESSAGES):
        message = email.message_from_bytes(raw, policy=email.policy.default)
        if recipient in message['To']:
            return re.search(r'token=([A-Za-z0-9_-]+)', message.get_content()).group(1)
    raise AssertionError('SMTP mail not received')

with tempfile.TemporaryDirectory(prefix='lsrs-runtime-') as directory:
    temp = Path(directory)
    smtp = SMTPServer(('127.0.0.1', 0), SMTPHandler)
    threading.Thread(target=smtp.serve_forever, daemon=True).start()
    log = (REPORT / 'runtime-server.log').open('w')
    jar = ROOT / 'build/libs/lsrs-sprint1.jar'
    if not jar.exists(): jar = ROOT / 'dist/lsrs-sprint1.jar'
    command = [os.environ.get('LSRS_JAVA', 'java'), '-jar', str(jar), '--spring.profiles.active=local',
               '--server.port=' + BASE.rsplit(':', 1)[1], '--app.public-base-url=' + BASE,
               '--app.mail.mode=smtp', '--spring.mail.host=127.0.0.1', '--spring.mail.port=' + str(smtp.server_address[1]),
               '--app.admin-emails=operator@example.test', '--app.jwt.key-file=' + str(temp / 'jwt.key'),
               '--spring.datasource.url=jdbc:h2:file:' + str(temp / 'lsrs') + ';DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000']
    process = None
    def start():
        global process
        process = subprocess.Popen(command, cwd=temp, stdout=log, stderr=log)
        for _ in range(120):
            if process.poll() is not None: raise RuntimeError('App failed; inspect runtime-server.log')
            try:
                if request('/api/app-info')[0] == 200:
                    # Empty local initializer completes in a very short interval after socket binds.
                    time.sleep(.5); return
            except (OSError, ValueError): pass
            time.sleep(.5)
        raise RuntimeError('Server startup timeout')
    def stop():
        process.terminate(); process.wait(timeout=25)
    try:
        start()
        check('local mode uses actual time and hides development mailbox in SMTP mode', lambda: (
            expect(request('/api/app-info')[1]['demo'] is False),
            expect(abs((dt.datetime.fromisoformat(request('/api/app-info')[1]['serverTime'].replace('Z', '+00:00')) - dt.datetime.now(dt.timezone.utc)).total_seconds()) < 5),
            expect(request('/api/dev/mailbox')[0] != 200)))
        student_body = {'studentNo': '20268888', 'email': 'persist@example.test', 'password': 'Persist123!'}
        code, result = request('/api/users', student_body)
        check('real JavaMailSender SMTP delivery creates pending account', lambda: (
            expect(code == 201), expect(result['deliveryStatus'] == 'SMTP_SENT'), expect(result['user']['status'] == 'PENDING'),
            expect(len(MESSAGES) == 1), expect(request('/api/auth/tokens', {'identifier': student_body['email'], 'password': student_body['password']})[0] == 403)))
        check('SMTP token verification activates account', lambda: expect(request('/api/email-verifications', {'token': latest_token(student_body['email'])})[1]['status'] == 'ACTIVE'))
        token = request('/api/auth/tokens', {'identifier': student_body['email'], 'password': student_body['password']})[1]['accessToken']
        request('/api/users', {'email': 'operator@example.test', 'password': 'Operator123!', 'requestedRole': 'ADMIN'})
        request('/api/email-verifications', {'token': latest_token('operator@example.test')})
        admin = request('/api/auth/tokens', {'identifier': 'operator@example.test', 'password': 'Operator123!'})[1]['accessToken']
        policy = request('/api/policies/current', token=token)[1]
        tomorrow = (dt.datetime.now(dt.timezone(dt.timedelta(hours=9))) + dt.timedelta(days=1)).date().isoformat()
        query = '/api/seats?date=' + tomorrow + '&start=10:00&end=11:00'
        seats = request(query, token=token)[1]['seats']; selected = next(s for s in seats if s['code'] == 'C03')
        code, reservation = request('/api/reservations', {'seatId': selected['id'], 'startAt': tomorrow + 'T10:00:00+09:00', 'endAt': tomorrow + 'T11:00:00+09:00', 'policyVersion': policy['versionNo']}, token)
        check('HTTP reservation persists with authoritative policy snapshot', lambda: (expect(code == 201), expect(reservation['status'] == 'RESERVED')))
        qr_before = request(f"/api/admin/seats/{selected['id']}/qr.png", token=admin, binary=True)[1]
        changed = request('/api/admin/policies', {'mode': 'EXAM', 'versionNo': policy['versionNo']}, admin, 'PUT')[1]
        check('admin policy updates through HTTP', lambda: expect(changed['maxMinutes'] == 180))
        key_before = (temp / 'jwt.key').read_bytes()
        stop(); start()
        check('file DB restart retains account and existing JWT', lambda: expect(request('/api/me', token=token)[1]['email'] == student_body['email']))
        check('file DB restart retains reservation and policy', lambda: (
            expect(request('/api/me/reservations', token=token)[1][0]['id'] == reservation['id']),
            expect(request('/api/policies/current', token=token)[1]['mode'] == 'EXAM'),
            expect(request('/api/reservations/' + str(reservation['id']), token=token)[1]['checkInDeadlineAt'] == reservation['checkInDeadlineAt'])))
        check('persistent signing key and QR survive restart', lambda: (
            expect((temp / 'jwt.key').read_bytes() == key_before),
            expect(request(f"/api/admin/seats/{selected['id']}/qr.png", token=admin, binary=True)[1] == qr_before)))
        REJECT_MAIL = True
        code, failure = request('/api/users', {'studentNo': '20268889', 'email': 'failure@example.test', 'password': 'Failure123!'})
        check('SMTP rejection keeps retryable pending account without false success', lambda: (
            expect(code == 201), expect(failure['deliveryStatus'] == 'FAILED'), expect(failure['user']['status'] == 'PENDING'),
            expect(request('/api/auth/tokens', {'identifier': 'failure@example.test', 'password': 'Failure123!'})[0] == 403)))
        REJECT_MAIL = False
        for _ in range(10): expect(request(query, token=token)[0] == 200)
        samples = []
        for _ in range(100):
            before = time.perf_counter(); expect(request(query, token=token)[0] == 200); samples.append((time.perf_counter() - before) * 1000)
        latency = {'requests': 100, 'warmup': 10, 'seatCount': 48, 'meanMs': round(statistics.mean(samples), 2),
                   'p95Ms': round(sorted(samples)[94], 2), 'maxMs': round(max(samples), 2),
                   'note': 'Serial localhost authenticated HTTP; exploratory measurement only. US05 load acceptance is Sprint 2.'}
        check('100 authenticated seat queries return successful real data', lambda: expect(len(samples) == 100))
        result = {'passed': len(CHECKS), 'failed': 0, 'checks': CHECKS, 'latency': latency,
                  'smtp': 'Local scripted TCP SMTP receiver; no external mail sent', 'persistence': 'H2 file DB + persistent random key; two app startups'}
        (REPORT / 'runtime-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(result, ensure_ascii=False), flush=True)
    finally:
        if process and process.poll() is None: stop()
        smtp.shutdown(); smtp.server_close(); log.close()
