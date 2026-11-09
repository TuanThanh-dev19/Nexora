// Node 24 built-ins only. All operational data goes through the real application API.
import { readFileSync, writeFileSync, mkdirSync, existsSync, renameSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../../', import.meta.url));
const baseIndex = process.argv.indexOf('--base-url');
const base = (baseIndex >= 0 ? process.argv[baseIndex + 1] : 'http://localhost:8080/api/v1').replace(/\/$/, '');
const url = new URL(base);
assert(['localhost', '127.0.0.1', '[::1]'].includes(url.hostname) && url.protocol === 'http:', 'Local API required.');
const password = 'Demo@Nexora2026!';
const localDir = path.join(root, '.data/demo', `port-${url.port || '80'}`);
mkdirSync(localDir, { recursive: true });
const journalFile = path.join(localDir, 'journal.json');
const journal = existsSync(journalFile) ? JSON.parse(readFileSync(journalFile, 'utf8')) : {};
function checkpoint(key, value) {
  journal[key] = value;
  writeFileSync(`${journalFile}.tmp`, JSON.stringify(journal, null, 2));
  renameSync(`${journalFile}.tmp`, journalFile);
}
async function api(route, { method = 'GET', token, body } = {}) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${base}${route}`, {
    method, headers, body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(30000),
  });
  if (!response.ok) throw new Error(`${method} ${route}: HTTP ${response.status} ${await response.text()}`);
  return response.status === 204 ? null : response.json();
}
async function login(email) {
  return (await api('/auth/login', { method: 'POST', body: { email, password } })).accessToken;
}
const admin = await login('admin@demo.nexora.test');
const get = (route, token = admin) => api(route, { token });
const post = (route, body, token = admin) => api(route, { method: 'POST', body, token });
const put = (route, body) => api(route, { method: 'PUT', body, token: admin });
async function list(route) {
  let all = [];
  for (let page = 0; ; page++) {
    const data = await get(`${route}${route.includes('?') ? '&' : '?'}size=100&page=${page}`);
    if (Array.isArray(data)) return data;
    all = all.concat(data.content);
    if (page + 1 >= data.totalPages) return all;
  }
}
const users = await list('/admin/users');
const accountSpecs = [
  ['manager', 'Trần Hoàng Nam', 'MANAGER'],
  ['linh', 'Nguyễn Thùy Linh', 'REQUESTER'],
  ['minh', 'Lê Đức Minh', 'REQUESTER'],
  ['chau', 'Phạm Bảo Châu', 'REQUESTER'],
  ['khoa', 'Võ Đăng Khoa', 'REQUESTER'],
  ['hanh', 'Đặng Mỹ Hạnh', 'REQUESTER'],
  ['tech.hvac', 'Nguyễn Quốc Bảo', 'TECHNICIAN'],
  ['tech.projector', 'Trần Anh Tuấn', 'TECHNICIAN'],
  ['tech.water', 'Lê Văn Hùng', 'TECHNICIAN'],
  ['tech.safety', 'Phạm Minh Quân', 'TECHNICIAN'],
  ['tech.backup', 'Võ Thành Đạt', 'TECHNICIAN'],
  ['tech.offline', 'Đỗ Hải Sơn', 'TECHNICIAN'],
];
const accounts = {};
for (const [key, fullName, role] of accountSpecs) {
  const email = `${key}@demo.nexora.test`;
  let user = users.find(u => u.email === email);
  if (!user) {
    user = await post('/admin/users', { email, password, fullName, status: 'ACTIVE', roles: [role] });
    users.push(user);
  }
  assert(user.status === 'ACTIVE' && user.roles.includes(role), `Existing demo user ${email} has incompatible status/roles; left unchanged.`);
  accounts[key] = { ...user, token: await login(email) };
}
console.log('Accounts: 1 demo Admin, 1 Manager, 5 Requesters, 6 Technicians.');

const locations = [];
async function loadLocations(parentId) {
  const children = await get(`/locations?active=false${parentId ? `&parentId=${parentId}` : ''}`);
  for (const child of children) {
    locations.push(child);
    await loadLocations(child.id);
  }
}
await loadLocations();
async function location(code, name, type, parentId) {
  let item = locations.find(l => l.code === code);
  if (!item) {
    item = await post('/admin/locations', { code, name, type, parentId, description: 'Khu thực hành demo Nexora; dữ liệu giả lập.' });
    locations.push(item);
  }
  return item;
}
const campus = await location('DEMO-CAMPUS', 'Campus thực hành Nexora', 'CAMPUS', null);
const rooms = [];
for (const [letter, name] of [['A', 'Giảng đường Alpha'], ['B', 'Phòng lab Beta']]) {
  const building = await location(`DEMO-BLD-${letter}`, name, 'BUILDING', campus.id);
  for (let floor = 1; floor <= 2; floor++) {
    const level = await location(`DEMO-FL-${letter}${floor}`, `Tầng ${floor} · ${name}`, 'FLOOR', building.id);
    for (let room = 1; room <= 4; room++) {
      const number = `${letter}${floor}0${room}`;
      rooms.push(await location(`DEMO-ROOM-${number}`, `Phòng ${number}`, 'ROOM', level.id));
    }
  }
}
const commonArea = await location('DEMO-COMMON-A1', 'Khu sinh hoạt chung · Alpha tầng 1', 'AREA', locations.find(l => l.code === 'DEMO-FL-A1').id);
const types = await list('/equipment-types');
for (const [code, name] of [['PROJECTOR', 'Máy chiếu'], ['AIR_CONDITIONER', 'Điều hòa'], ['DEMO-WATER', 'Thiết bị cấp thoát nước'], ['DEMO-ELECTRIC', 'Thiết bị điện và an toàn']]) {
  if (!types.some(t => t.code === code)) types.push(await post('/admin/equipment-types', { code, name, description: 'Danh mục phục vụ demo campus.' }));
}
const assets = await list('/assets');
async function asset(code, equipmentCode, room, description) {
  let item = assets.find(a => a.assetCode === code);
  if (!item) {
    item = await post('/assets', { assetCode: code, equipmentTypeId: types.find(t => t.code === equipmentCode).id,
      locationId: room.id, serialNumber: `NX-DEMO-${code}`, description, purchasedAt: '2025-08-15', warrantyUntil: '2028-08-15' });
    assets.push(item);
  }
  return item;
}
for (const room of rooms) {
  const number = room.code.replace('DEMO-ROOM-', '');
  await asset(`DEMO-PRJ-${number}`, 'PROJECTOR', room, `Máy chiếu Epson EB-X49 · ${room.name} · tài sản giả lập`);
  await asset(`DEMO-AC-${number}`, 'AIR_CONDITIONER', room, `Điều hòa Daikin inverter 2 HP · ${room.name} · tài sản giả lập`);
}
for (const [suffix, targetStatus, reason] of [
  ['SPARE', 'ACTIVE', null], ['MAINT', 'UNDER_MAINTENANCE', 'Vệ sinh định kỳ dàn lạnh và kiểm tra áp suất.'],
  ['BROKEN', 'OUT_OF_SERVICE', 'Hỏng bo điều khiển; chờ linh kiện thay thế.'], ['RETIRED', 'RETIRED', 'Thiết bị hết vòng đời, đã bàn giao thay mới.'],
]) {
  const code = `DEMO-AC-${suffix}`;
  const existed = assets.some(a => a.assetCode === code);
  const item = await asset(code, 'AIR_CONDITIONER', rooms[0], `Điều hòa dự phòng · ${suffix} · dữ liệu demo`);
  if (!existed && targetStatus !== 'ACTIVE') await post(`/assets/${item.id}/status`, { targetStatus, reason, version: item.version });
}

const skills = await get('/admin/skills');
const slas = await get('/sla-policies');
const techSpecs = [
  ['tech.hvac', ['HVAC'], true], ['tech.projector', ['PROJECTOR'], true],
  ['tech.water', ['PLUMBING'], true], ['tech.safety', ['SAFETY'], true],
  ['tech.backup', ['HVAC', 'PROJECTOR', 'PLUMBING', 'SAFETY'], true],
  ['tech.offline', ['HVAC', 'PROJECTOR'], false],
];
for (const [key, skillCodes, available] of techSpecs) {
  const id = accounts[key].id;
  const marker = `technician:${id}:${accounts[key].createdAt}`;
  if (journal[marker]?.complete) continue;
  let profile = await put(`/technicians/${id}`, { available, maxActiveTickets: 8 });
  profile = await put(`/technicians/${id}/skills`, { version: profile.version,
    skills: skillCodes.map(code => ({ skillId: skills.find(s => s.code === code).id, proficiencyLevel: 4 })) });
  await put(`/technicians/${id}/service-areas`, { version: profile.version, locationIds: [campus.id] });
  checkpoint(marker, { complete: true });
}
console.log('Catalog: 24 demo locations, 36 assets; skills and service areas configured.');

function field(fieldKey, label, fieldType, validationRules = {}, extra = {}) {
  return { fieldKey, label, fieldType, validationRules, required: true, active: true, displayOrder: 0, options: [], ...extra };
}
const categorySpecs = [
  { key: 'HVAC', name: 'Điều hòa · Campus thực hành', skill: 'HVAC', priority: 'NORMAL', equipment: true,
    fields: [field('room_number', 'Số phòng', 'TEXT', { maxLength: 30 }),
      field('symptom', 'Hiện tượng', 'SELECT', {}, { options: [
        { optionValue: 'NOT_COOLING', optionLabel: 'Không mát', displayOrder: 0, active: true },
        { optionValue: 'WATER_LEAK', optionLabel: 'Chảy nước', displayOrder: 1, active: true },
        { optionValue: 'NOISE', optionLabel: 'Tiếng ồn bất thường', displayOrder: 2, active: true }] }),
      field('occurred_at', 'Thời điểm phát hiện', 'DATETIME')] },
  { key: 'PROJECTOR', name: 'Máy chiếu · Campus thực hành', skill: 'PROJECTOR', priority: 'NORMAL', equipment: true,
    fields: [field('asset_code', 'Mã thiết bị', 'TEXT', { minLength: 3, maxLength: 80 }),
      field('indicator_description', 'Đèn báo và hiện tượng', 'TEXTAREA', { minLength: 10, maxLength: 500 }),
      field('screen_image', 'Ảnh minh họa sự cố', 'IMAGE', { minFiles: 1, maxFiles: 3, maxSizeBytes: 5242880, allowedTypes: ['image/png', 'image/jpeg'] })] },
  { key: 'WATER', name: 'Cấp thoát nước · Campus thực hành', skill: 'PLUMBING', priority: 'HIGH', equipment: false,
    fields: [field('precise_location', 'Vị trí rò rỉ cụ thể', 'TEXTAREA', { minLength: 10, maxLength: 500 }),
      field('leak_image', 'Ảnh minh họa vị trí', 'IMAGE', { minFiles: 1, maxFiles: 3, maxSizeBytes: 5242880, allowedTypes: ['image/png', 'image/jpeg'] })] },
  { key: 'SAFETY', name: 'An toàn cơ sở vật chất · Campus thực hành', skill: 'SAFETY', priority: 'URGENT', equipment: false,
    fields: [field('impact_scope', 'Phạm vi ảnh hưởng', 'TEXTAREA', { minLength: 20, maxLength: 1000 }),
      field('people_affected', 'Số người có thể bị ảnh hưởng', 'NUMBER', { min: 1, max: 500 })] },
];
const categories = await get('/admin/categories');
const forms = {};
for (const spec of categorySpecs) {
  const code = `DEMO_${spec.key}`;
  let category = categories.find(c => c.code === code);
  if (!category) category = await post('/admin/categories', { code, name: spec.name,
    description: 'Tình huống giả lập sát thực tế để trình diễn toàn bộ quy trình xử lý.',
    assetPolicy: spec.equipment ? 'REQUIRED' : 'OPTIONAL', locationRequired: true, defaultPriority: spec.priority, active: true });
  const versions = await get(`/admin/categories/${category.id}/form-versions`);
  let form;
  if (versions.some(v => v.status === 'PUBLISHED')) form = await get(`/categories/${category.id}/published-form`);
  else {
    const draft = versions.find(v => v.status === 'DRAFT');
    form = draft ? await get(`/admin/form-versions/${draft.id}`) : await post(`/admin/categories/${category.id}/form-versions/drafts`);
    form = await put(`/admin/form-versions/${form.id}`, { revision: form.revision,
      requiresEquipment: spec.equipment, minAttachments: ['PROJECTOR', 'WATER'].includes(spec.key) ? 1 : 0,
      defaultSkillId: skills.find(s => s.code === spec.skill).id,
      defaultSlaPolicyId: slas.find(s => s.priority === spec.priority && s.active).id,
      fields: spec.fields.map((f, displayOrder) => ({ ...f, displayOrder })) });
    form = await post(`/admin/form-versions/${form.id}/publish`, { revision: form.revision });
  }
  forms[spec.key] = { category, form };
}

// Real submissions and transitions create SQL history, private files and Mongo notifications.
const situations = {
  HVAC: [
    ['Điều hòa không mát trong giờ học', 'Máy chạy liên tục nhưng nhiệt độ phòng vẫn 30°C. Lớp 35 sinh viên cần kiểm tra trước ca học tiếp theo.', 'NOT_COOLING', 'Đã vệ sinh lưới lọc, kiểm tra gas và chạy thử 20 phút. Nhiệt độ phòng ổn định ở 25°C.'],
    ['Điều hòa nhỏ nước xuống bàn học', 'Nước đọng từ dàn lạnh nhỏ xuống hai bàn phía cửa sổ. Đã di chuyển đồ dùng khỏi khu vực để tránh hư hỏng.', 'WATER_LEAK', 'Đã thông ống thoát nước ngưng và chỉnh độ dốc máng. Chạy thử không còn rò nước.'],
    ['Dàn lạnh rung và phát tiếng ồn', 'Khi bật chế độ quạt cao, dàn lạnh rung mạnh và phát tiếng rè, ảnh hưởng buổi thuyết trình.', 'NOISE', 'Đã siết giá treo, cân chỉnh quạt và thay đệm chống rung. Máy vận hành êm.'],
  ],
  PROJECTOR: [
    ['Máy chiếu không nhận tín hiệu HDMI', 'Đã thử hai máy tính và đổi cổng HDMI nhưng màn chiếu vẫn báo No Signal. Cần xử lý trước tiết thực hành.', null, 'Đã thay cáp HDMI và cấu hình lại nguồn vào. Kiểm tra trình chiếu với hai laptop thành công.'],
    ['Hình máy chiếu bị mờ và lệch màu', 'Nội dung chữ nhỏ bị nhòe, hình có ám vàng. Hiện tượng xảy ra với cả slide và video.', null, 'Đã vệ sinh kính, chỉnh tiêu cự và cân bằng màu. Hình ảnh rõ ở toàn bộ màn chiếu.'],
    ['Máy chiếu tự tắt sau khoảng 10 phút', 'Đèn nhiệt độ nhấp nháy đỏ rồi máy tự tắt. Đã ngừng sử dụng để tránh ảnh hưởng bóng đèn.', null, 'Đã vệ sinh lọc bụi, kiểm tra quạt tản nhiệt và chạy tải 30 phút. Không còn tự tắt.'],
  ],
  WATER: [
    ['Rò nước tại khớp nối dưới bồn rửa', 'Nước chảy thành giọt liên tục dưới bồn rửa gần hành lang. Sàn có vũng nước nhỏ, đã đặt biển cảnh báo.', null, 'Đã thay gioăng và siết khớp nối. Kiểm tra áp lực 15 phút không còn rò rỉ.'],
    ['Vòi nước không khóa kín', 'Vòi nước vẫn chảy khi đã xoay hết van. Cần xử lý để tránh lãng phí và ngập sàn khu vực chung.', null, 'Đã thay lõi van và kiểm tra đóng mở 10 lần. Vòi khóa kín, lưu lượng bình thường.'],
    ['Thoát sàn chậm gây đọng nước', 'Nước vệ sinh sàn thoát rất chậm, xuất hiện mùi khó chịu. Khu vực có nhiều người qua lại giữa các tiết học.', null, 'Đã thông đường thoát, vệ sinh bẫy mùi và thử xả nước. Sàn khô, thoát nước bình thường.'],
  ],
  SAFETY: [
    ['Ổ cắm lỏng tại khu vực bàn học', 'Mặt ổ cắm bị lỏng, chân tiếp xúc có dấu hiệu đổi màu. Đã ngắt sử dụng và báo trực tầng để khoanh vùng.', null, 'Đã ngắt nguồn, thay ổ cắm và đo kiểm cách điện. Nguồn điện hoạt động ổn định.'],
    ['Đèn hành lang chập chờn', 'Hai bóng đèn hành lang bật tắt liên tục, ánh sáng yếu vào cuối buổi học, ảnh hưởng việc đi lại.', null, 'Đã thay bộ nguồn LED và kiểm tra dây nối. Hành lang đủ sáng, không còn chập chờn.'],
    ['Gạch lối đi bong gây nguy cơ vấp ngã', 'Một viên gạch nhô lên tại lối đi bộ. Đã đặt cọc cảnh báo; cần cố định trước giờ tan học đông người.', null, 'Đã cố định viên gạch, xử lý mép tiếp giáp và kiểm tra mặt phẳng. Lối đi được mở lại an toàn.'],
  ],
};
const states = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
const technicianFor = { HVAC: 'tech.hvac', PROJECTOR: 'tech.projector', WATER: 'tech.water', SAFETY: 'tech.safety' };
const requesterKeys = ['linh', 'minh', 'chau', 'khoa', 'hanh'];
const existingTickets = await list('/tickets');
const demoTickets = [];
let created = 0;
for (let stage = 0; stage < states.length; stage++) {
  for (let kindIndex = 0; kindIndex < categorySpecs.length; kindIndex++) {
    const spec = categorySpecs[kindIndex];
    const index = stage * 4 + kindIndex;
    const marker = `DEMO-${String(index + 1).padStart(2, '0')}`;
    const scenario = situations[spec.key][stage % 3];
    const room = rooms[index % rooms.length];
    const incidentLocation = spec.key === 'SAFETY' && stage % 3 === 2 ? commonArea : room;
    const reporter = accounts[requesterKeys[index % requesterKeys.length]];
    const tech = accounts[technicianFor[spec.key]];
    let ticket = existingTickets.find(t => t.title.startsWith(`[${marker}] `));
    if (ticket && journal[marker]?.complete !== false) {
      demoTickets.push(await get(`/tickets/${ticket.id}`));
      continue;
    }
    const { category, form } = forms[spec.key];
    const equipment = assets.find(a => a.assetCode === `DEMO-${spec.key === 'HVAC' ? 'AC' : 'PRJ'}-${room.code.replace('DEMO-ROOM-', '')}`);
    const imageField = spec.fields.find(f => f.fieldType === 'IMAGE');
    if (!ticket) {
      checkpoint(marker, { complete: false, target: states[stage] });
      const values = {
        room_number: room.name, symptom: scenario[2], occurred_at: new Date(Date.now() - 3600000).toISOString(),
        asset_code: equipment?.assetCode, indicator_description: scenario[1],
        precise_location: `${room.name}, khu bồn rửa gần hành lang. ${scenario[1]}`,
        impact_scope: `${scenario[1]} Khoảng 35 sinh viên và giảng viên sử dụng khu vực này.`, people_affected: 35,
      };
      const payload = { categoryId: category.id, formVersionId: form.id,
        locationId: incidentLocation.id,
        ...(spec.equipment ? { assetId: equipment.id } : {}),
        title: `[${marker}] ${scenario[0]} · ${incidentLocation.name}`,
        description: `${scenario[1]}\nBộ dữ liệu demo giả lập; ảnh đính kèm là sơ đồ minh họa.`,
        dynamicValues: spec.fields.filter(f => f.fieldType !== 'IMAGE').map(f => ({ fieldKey: f.fieldKey, value: values[f.fieldKey] })),
        attachments: imageField ? [{ fileIndex: 0, fieldKey: imageField.fieldKey }] : [] };
      const body = new FormData();
      body.append('ticket', new Blob([JSON.stringify(payload)], { type: 'application/json' }), 'ticket.json');
      if (imageField) body.append('files', new Blob([readFileSync(path.join(root, 'infrastructure/demo/evidence', `${spec.key.toLowerCase()}.png`))], { type: 'image/png' }), `${marker}-minh-hoa.png`);
      ticket = await post('/tickets', body, reporter.token);
      created++;
    }
    const refresh = async () => { ticket = await get(`/tickets/${ticket.id}`); };
    await refresh();
    if (stage >= 1 && ticket.status === 'SUBMITTED') {
      await post(`/tickets/${ticket.id}/review`, { version: ticket.version }, accounts.manager.token); await refresh();
    }
    if (stage >= 2 && ticket.status === 'UNDER_REVIEW') {
      await post(`/tickets/${ticket.id}/assign`, { version: ticket.version, technicianId: tech.id }, accounts.manager.token); await refresh();
    }
    // One ready-to-start ticket shows a genuine reassignment history.
    if (stage === 2 && spec.key === 'HVAC' && ticket.assignmentHistory.length === 1) {
      await post(`/tickets/${ticket.id}/reassign`, { version: ticket.version, technicianId: accounts['tech.backup'].id,
        reason: 'Điều phối sang kỹ thuật viên dự phòng để cân bằng lịch công tác trong ca.' }, accounts.manager.token); await refresh();
    }
    if (stage >= 3 && ticket.status === 'ASSIGNED') {
      await post(`/tickets/${ticket.id}/start`, { version: ticket.version }, tech.token); await refresh();
    }
    if (stage >= 3 && ticket.status === 'IN_PROGRESS') {
      const logs = await get(`/tickets/${ticket.id}/work-logs`);
      if (!logs.length) {
        await post(`/tickets/${ticket.id}/work-logs`, { version: ticket.version, logType: 'DIAGNOSIS',
          content: `Đã khảo sát ${incidentLocation.name}, xác nhận hiện tượng người báo mô tả. Khoanh vùng và kiểm tra bộ phận liên quan.`,
          startedAt: new Date().toISOString(), minutesSpent: 15 }, tech.token); await refresh();
      }
      if (stage >= 4) {
        if (logs.length < 2) {
          await post(`/tickets/${ticket.id}/work-logs`, { version: ticket.version, logType: 'REPAIR', content: scenario[3],
            startedAt: new Date().toISOString(), minutesSpent: 30 }, tech.token); await refresh();
        }
        if (!ticket.attachments.some(a => a.attachmentType === 'WORK_RESULT')) {
          const body = new FormData();
          body.append('metadata', JSON.stringify({ attachmentType: 'WORK_RESULT' }));
          body.append('files', new Blob([readFileSync(path.join(root, 'infrastructure/demo/evidence/result.png'))], { type: 'image/png' }), `${marker}-ket-qua-minh-hoa.png`);
          await post(`/tickets/${ticket.id}/attachments`, body, tech.token);
        }
        await post(`/tickets/${ticket.id}/resolve`, { version: ticket.version, resolutionOutcome: 'FIXED', resolutionSummary: scenario[3] }, tech.token); await refresh();
      }
    }
    if (stage >= 5 && ticket.status === 'RESOLVED') {
      await post(`/tickets/${ticket.id}/close`, { version: ticket.version,
        reason: 'Đã kiểm tra sử dụng lại, xác nhận sự cố được xử lý và đồng ý đóng phiếu.' }, reporter.token); await refresh();
    }
    assert.equal(ticket.status, states[stage], marker);
    checkpoint(marker, { complete: true, id: ticket.id, target: states[stage] });
    demoTickets.push(ticket);
    console.log(`${marker}: ${ticket.ticketNumber} -> ${ticket.status}`);
  }
}

// Verify persisted detail, ownership, technician work queues, protected image bytes and notifications.
for (const key of requesterKeys) {
  const page = await get('/tickets?size=100', accounts[key].token);
  assert(page.content.every(t => t.reporter.id === accounts[key].id), `Requester scope: ${key}`);
}
for (const [key] of techSpecs) {
  const page = await get('/technicians/me/assigned-tickets?size=100', accounts[key].token);
  const expected = demoTickets.filter(t => ['ASSIGNED', 'IN_PROGRESS'].includes(t.status)
    && t.currentAssignment?.technician.technicianId === accounts[key].id);
  assert(expected.every(t => page.content.some(row => row.id === t.id)), `Missing active work: ${key}`);
}
let attachmentsVerified = 0;
for (const ticket of demoTickets) {
  for (const a of ticket.attachments) {
    const response = await fetch(`${base}/tickets/${ticket.id}/attachments/${a.id}/content`, { headers: { Authorization: `Bearer ${admin}` }, signal: AbortSignal.timeout(30000) });
    assert.equal(response.status, 200);
    assert.equal((await response.arrayBuffer()).byteLength, a.sizeBytes);
    attachmentsVerified++;
  }
}
const summary = await get('/dashboard/summary', accounts.manager.token);
const notificationCount = (await get('/notifications?size=100', accounts.linh.token)).totalElements;
assert(notificationCount > 0, 'Expected persisted requester notifications in MongoDB.');
const result = { seededAt: new Date().toISOString(), baseUrl: base, createdTicketsThisRun: created,
  demoAccounts: 13, demoLocations: 24, demoAssets: 36, demoCategories: 4,
  tickets: demoTickets.map(t => ({ id: t.id, number: t.ticketNumber, title: t.title, status: t.status, reporter: t.reporter.fullName })),
  statusCounts: Object.fromEntries(states.map(s => [s, demoTickets.filter(t => t.status === s).length])),
  attachmentsVerified, requesterNotificationsVerified: notificationCount, dashboard: summary };
writeFileSync(path.join(localDir, 'summary.json'), JSON.stringify(result, null, 2));
console.log(JSON.stringify({ createdTicketsThisRun: created, statusCounts: result.statusCounts, attachmentsVerified, dashboard: summary }, null, 2));
console.log(`Verified. Summary: ${path.join(localDir, 'summary.json')}`);
