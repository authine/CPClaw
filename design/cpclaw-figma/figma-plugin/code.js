const C = {
  bg: '#F5F7FA',
  surface: '#FFFFFF',
  surface2: '#F9FAFB',
  text: '#182230',
  muted: '#5F6B7A',
  subtle: '#8A94A3',
  line: '#D8DEE8',
  lineSoft: '#E8EDF3',
  navy: '#0F172A',
  navy2: '#16233A',
  navy3: '#1D2B46',
  blue: '#2563EB',
  blueSoft: '#E8F0FF',
  cyan: '#0E7490',
  green: '#168A4A',
  greenSoft: '#E8F6EE',
  amber: '#B06B00',
  amberSoft: '#FFF4DF',
  red: '#C4382B',
  redSoft: '#FFF0EE',
  purple: '#6D4AFF'
};

const FONT = {
  regular: { family: 'Inter', style: 'Regular' },
  medium: { family: 'Inter', style: 'Medium' },
  bold: { family: 'Inter', style: 'Bold' }
};

const safeReactions = [];

function rgb(hex, opacity = 1) {
  const n = hex.replace('#', '');
  return {
    type: 'SOLID',
    color: {
      r: parseInt(n.slice(0, 2), 16) / 255,
      g: parseInt(n.slice(2, 4), 16) / 255,
      b: parseInt(n.slice(4, 6), 16) / 255
    },
    opacity
  };
}

function clearPage(page) {
  for (const child of [...page.children]) child.remove();
}

function frame(parent, name, x, y, w, h, fill = C.surface, radius = 0, stroke = null) {
  const f = figma.createFrame();
  f.name = name;
  f.x = x;
  f.y = y;
  f.resize(w, h);
  f.fills = fill ? [rgb(fill)] : [];
  f.strokes = stroke ? [rgb(stroke)] : [];
  f.strokeWeight = stroke ? 1 : 0;
  f.cornerRadius = radius;
  f.clipsContent = false;
  parent.appendChild(f);
  return f;
}

function rect(parent, name, x, y, w, h, fill, radius = 0, stroke = null) {
  const r = figma.createRectangle();
  r.name = name;
  r.x = x;
  r.y = y;
  r.resize(w, h);
  r.fills = fill ? [rgb(fill)] : [];
  r.strokes = stroke ? [rgb(stroke)] : [];
  r.strokeWeight = stroke ? 1 : 0;
  r.cornerRadius = radius;
  parent.appendChild(r);
  return r;
}

function line(parent, name, x, y, w, color = C.lineSoft) {
  rect(parent, name, x, y, w, 1, color, 0);
}

function text(parent, name, value, x, y, w, size = 14, color = C.text, font = FONT.regular, lineHeight = 20) {
  const t = figma.createText();
  t.name = name;
  t.x = x;
  t.y = y;
  t.resize(w, Math.max(lineHeight, size + 6));
  t.fontName = font;
  t.fontSize = size;
  t.fills = [rgb(color)];
  t.lineHeight = { unit: 'PIXELS', value: lineHeight };
  t.textAutoResize = 'HEIGHT';
  t.characters = value;
  parent.appendChild(t);
  return t;
}

function chip(parent, label, x, y, variant = 'neutral', w = null) {
  const styles = {
    neutral: [C.surface, C.muted, C.line],
    blue: [C.blueSoft, C.blue, '#C7D7FF'],
    green: [C.greenSoft, C.green, '#BDE4CD'],
    amber: [C.amberSoft, C.amber, '#FFD99A'],
    red: [C.redSoft, C.red, '#FFC5BF'],
    dark: [C.navy3, '#DDE7F5', '#405276']
  }[variant] || [C.surface, C.muted, C.line];
  const width = w || Math.max(54, label.length * 10 + 18);
  const c = frame(parent, `Tag / ${label}`, x, y, width, 24, styles[0], 8, styles[2]);
  text(c, 'Label', label, 8, 3, width - 16, 12, styles[1], FONT.medium, 18);
  return c;
}

function button(parent, label, x, y, w = 96, kind = 'secondary') {
  const map = {
    primary: [C.blue, '#FFFFFF', C.blue],
    secondary: [C.surface, C.text, C.line],
    quiet: [C.surface2, C.text, C.lineSoft],
    danger: [C.red, '#FFFFFF', C.red],
    dark: [C.navy2, '#FFFFFF', '#334362']
  }[kind];
  const b = frame(parent, `Button / ${label}`, x, y, w, 34, map[0], 6, map[2]);
  text(b, 'Label', label, 12, 7, w - 24, 13, map[1], FONT.medium, 18);
  return b;
}

function input(parent, label, value, x, y, w, masked = false) {
  text(parent, `${label} label`, label, x, y, w, 12, C.muted, FONT.regular, 16);
  const box = frame(parent, `${label} input`, x, y + 22, w, 36, C.surface, 6, C.line);
  text(box, 'Value', masked ? '************' : value, 10, 8, w - 20, 13, masked ? C.subtle : C.text, FONT.regular, 18);
  return box;
}

function card(parent, name, x, y, w, h, title, subtitle = '') {
  const c = frame(parent, name, x, y, w, h, C.surface, 6, C.lineSoft);
  if (title) text(c, 'Title', title, 12, 10, w - 24, 14, C.text, FONT.bold, 20);
  if (subtitle) text(c, 'Subtitle', subtitle, 12, 34, w - 24, 12, C.muted, FONT.regular, 18);
  return c;
}

function stat(parent, label, value, x, y, w, tone = 'default') {
  const colors = { default: C.text, green: C.green, amber: C.amber, red: C.red, blue: C.blue };
  const s = frame(parent, `Stat / ${label}`, x, y, w, 76, C.surface2, 6, C.lineSoft);
  text(s, 'Value', value, 12, 12, w - 24, 22, colors[tone] || C.text, FONT.bold, 28);
  text(s, 'Label', label, 12, 44, w - 24, 12, C.muted, FONT.regular, 18);
  return s;
}

function table(parent, x, y, w, columns, rows) {
  const rowH = 38;
  const h = rowH * (rows.length + 1);
  const t = frame(parent, 'Table / Markdown result', x, y, w, h, C.surface, 6, C.lineSoft);
  rect(t, 'Header bg', 0, 0, w, rowH, C.surface2, 6);
  const widths = columns.map(c => c.w);
  let left = 0;
  columns.forEach((c, i) => {
    text(t, `Head ${c.label}`, left + 10, 10, widths[i] - 20, 12, C.muted, FONT.bold, 18);
    left += widths[i];
  });
  rows.forEach((row, r) => {
    const top = rowH * (r + 1);
    line(t, `Row line ${r}`, 0, top, w, C.lineSoft);
    let lx = 0;
    row.forEach((cell, i) => {
      text(t, `Cell ${r}-${i}`, String(cell), lx + 10, top + 9, widths[i] - 20, 12, C.text, FONT.regular, 18);
      lx += widths[i];
    });
  });
  return t;
}

function conversationItem(parent, x, y, w, titleValue, meta, active = false) {
  const f = frame(parent, `Conversation / ${titleValue}`, x, y, w, 58, active ? C.navy3 : C.navy, 6, active ? '#405276' : null);
  text(f, 'Title', titleValue, 10, 9, w - 20, 13, active ? '#FFFFFF' : '#CFD8E8', FONT.medium, 18);
  text(f, 'Meta', meta, 10, 32, w - 20, 12, '#91A0B6', FONT.regular, 16);
  return f;
}

function message(parent, kind, x, y, w, body, meta = '') {
  const isUser = kind === 'user';
  const h = body.length > 70 ? 118 : 74;
  const m = frame(parent, `Message / ${kind}`, x, y, w, h, null, 0);
  const avatarFill = isUser ? '#D8E4FF' : '#E8EDF3';
  rect(m, 'Avatar', 0, 0, 22, 22, avatarFill, 6);
  text(m, 'Avatar label', isUser ? '我' : 'AI', 5, 3, 14, 10, C.text, FONT.bold, 14);
  text(m, 'Meta', meta || (isUser ? '你 09:32' : 'CPClaw 09:32'), 30, 1, w - 30, 12, C.muted, FONT.regular, 16);
  const b = frame(m, 'Bubble', isUser ? 0 : 30, 30, isUser ? w : w - 30, h - 30, isUser ? C.blueSoft : C.surface, 6, isUser ? '#C8D8FF' : C.lineSoft);
  text(b, 'Body', body, 12, 10, (isUser ? w : w - 30) - 24, 13, C.text, FONT.regular, 20);
  return m;
}

function timeline(parent, x, y, w, steps) {
  const f = frame(parent, 'Agent execution timeline', x, y, w, steps.length * 58 + 16, C.surface, 6, C.lineSoft);
  text(f, 'Title', 'Agent 执行时间线', 12, 10, w - 24, 14, C.text, FONT.bold, 20);
  steps.forEach((s, i) => {
    const top = 42 + i * 58;
    rect(f, `Dot ${i}`, 14, top + 2, 10, 10, s.color || C.blue, 5);
    if (i < steps.length - 1) rect(f, `Connector ${i}`, 18, top + 18, 2, 35, C.lineSoft, 0);
    text(f, `Step ${i}`, s.title, 34, top - 2, w - 46, 13, C.text, FONT.medium, 18);
    text(f, `Step meta ${i}`, s.meta, 34, top + 20, w - 46, 12, C.muted, FONT.regular, 16);
  });
  return f;
}

function metadataMatch(parent, x, y, w, titleValue, score, meta, tone = 'green') {
  const f = frame(parent, `Metadata match / ${titleValue}`, x, y, w, 82, C.surface, 6, C.lineSoft);
  text(f, 'Title', titleValue, 10, 10, w - 112, 13, C.text, FONT.bold, 18);
  chip(f, `${score}% 匹配`, w - 92, 8, tone, 82);
  rect(f, 'Score bg', 10, 40, w - 20, 5, '#EDF2F7', 6);
  rect(f, 'Score fg', 10, 40, (w - 20) * score / 100, 5, tone === 'green' ? C.green : C.amber, 6);
  text(f, 'Meta', meta, 10, 54, w - 20, 12, C.muted, FONT.regular, 16);
  return f;
}

function statusCard(parent, x, y, w, label, value, state) {
  const dotColor = { ok: C.green, warn: '#D58A00', err: C.red, idle: C.subtle }[state] || C.subtle;
  const f = frame(parent, `Status / ${label}`, x, y, w, 54, C.surface, 6, C.lineSoft);
  rect(f, 'Dot', 12, 22, 9, 9, dotColor, 5);
  text(f, 'Label', label, 32, 9, w - 44, 12, C.muted, FONT.regular, 16);
  text(f, 'Value', value, 32, 28, w - 44, 13, C.text, FONT.bold, 18);
  return f;
}

function riskConfirmCard(parent, x, y, w, state = 'pending') {
  const f = frame(parent, 'High risk confirmation card', x, y, w, state === 'pending' ? 266 : 142, C.surface, 6, '#FFB6AE');
  rect(f, 'Header bg', 0, 0, w, 42, C.redSoft, 6);
  text(f, 'Title', '高风险操作确认', 12, 11, w - 150, 14, '#A62D24', FONT.bold, 20);
  chip(f, state === 'confirmed' ? '已确认' : state === 'cancelled' ? '已取消' : '写入云枢', w - 112, 9, state === 'cancelled' ? 'amber' : state === 'confirmed' ? 'green' : 'red', 92);
  if (state === 'pending') {
    const rows = [
      ['操作类型', '新增客户跟进记录'],
      ['影响对象', '客户：云栖制造；订单：SO-2026-0618-009'],
      ['风险等级', '高风险'],
      ['系统将执行', '调用客户管理 / 跟进记录 / create Action'],
      ['执行状态', '确认前不会执行真实写入']
    ];
    rows.forEach((r, i) => {
      text(f, `Key ${i}`, r[0], 14, 58 + i * 28, 88, 12, C.muted, FONT.regular, 18);
      text(f, `Value ${i}`, r[1], 116, 58 + i * 28, w - 130, 12, i === 4 ? C.red : C.text, i === 4 ? FONT.bold : FONT.regular, 18);
    });
    line(f, 'Action line', 0, 206, w, C.lineSoft);
    button(f, '取消', w - 184, 222, 76, 'secondary');
    button(f, '确认执行', w - 100, 222, 86, 'danger');
  } else {
    text(f, 'Result', state === 'confirmed' ? '已提交云枢 Action，写入结果进入审计详情。' : '用户已取消，未执行任何真实写入。', 14, 60, w - 28, 13, C.text, FONT.regular, 20);
    chip(f, 'AGT-RUN-20260620-8452', 14, 98, 'blue', 176);
  }
  return f;
}

function drawShell(name, x, y, rightTitle = 'Agent 执行过程') {
  const root = frame(figma.currentPage, name, x, y, 1440, 1024, C.bg, 0);
  root.setRelaunchData({ open: 'CPClaw prototype frame' });
  const side = frame(root, 'Left / history sessions', 0, 0, 260, 1024, C.navy, 0);
  line(side, 'Side divider', 259, 0, 1024, '#1F2A44');
  rect(side, 'Logo mark', 18, 17, 30, 30, C.surface, 6);
  text(side, 'Logo label', 'CP', 25, 23, 18, 11, C.navy, FONT.bold, 14);
  text(side, 'Brand', 'CPClaw', 60, 14, 120, 14, '#FFFFFF', FONT.bold, 20);
  text(side, 'Brand sub', '云枢超级智能体', 60, 36, 140, 12, '#96A4BA', FONT.regular, 16);
  line(side, 'Brand line', 0, 64, 260, '#22304D');
  const newChat = button(side, '+ 新建会话', 14, 78, 232, 'dark');
  text(side, 'History label', '历史会话', 14, 130, 100, 12, '#91A0B6', FONT.regular, 16);
  conversationItem(side, 14, 154, 232, '销售订单查询与跟进', '刚刚  ·  Run 8451', true);
  conversationItem(side, 14, 220, 232, '合同审批流程状态', '10:24  ·  中风险', false);
  conversationItem(side, 14, 286, 232, '客户主数据字段说明', '昨天  ·  只读', false);
  conversationItem(side, 14, 352, 232, '费用报销附件检查', '周三  ·  失败重试', false);
  button(side, '设置与连接', 14, 860, 232, 'dark');
  button(side, '元数据初始化', 14, 902, 232, 'dark');
  button(side, '审计记录', 14, 944, 232, 'dark');

  const main = frame(root, 'Center / chat workspace', 260, 0, 840, 1024, C.bg, 0);
  rect(main, 'Topbar bg', 0, 0, 840, 64, C.surface, 0);
  line(main, 'Topbar line', 0, 64, 840, C.lineSoft);
  text(main, 'Page title', '对话式云枢业务执行工作台', 20, 14, 330, 17, C.text, FONT.bold, 24);
  text(main, 'Page desc', '自然语言查询、解释、执行与审计追踪', 20, 40, 330, 12, C.muted, FONT.regular, 16);
  const auditBtn = button(main, '审计查询', 566, 15, 84, 'secondary');
  const metaBtn = button(main, '初始化', 658, 15, 72, 'secondary');
  const setBtn = button(main, '配置连接', 738, 15, 82, 'primary');
  rect(main, 'Status strip bg', 0, 65, 840, 78, '#FBFCFE', 0);
  statusCard(main, 20, 77, 256, '云枢账号', '待配置', 'warn');
  statusCard(main, 292, 77, 256, '模型 API', '已保存，不回显 Key', 'ok');
  statusCard(main, 564, 77, 256, '管理员元数据', '待初始化', 'warn');

  const right = frame(root, 'Right / execution panel', 1100, 0, 340, 1024, '#FBFCFE', 0);
  rect(right, 'Right head bg', 0, 0, 340, 64, C.surface, 0);
  line(right, 'Right head line', 0, 64, 340, C.lineSoft);
  text(right, 'Right title', rightTitle, 16, 21, 220, 14, C.text, FONT.bold, 20);
  return { root, side, main, right, buttons: { newChat, auditBtn, metaBtn, setBtn } };
}

function buildWorkspaceEmpty(x, y) {
  const shell = drawShell('01 工作台 / 首次进入与待配置', x, y);
  const main = shell.main;
  message(main, 'assistant', 24, 172, 720, '可以直接开始对话。当前环境还缺少基础配置，涉及真实写入前我会先展示确认卡片，确认前不会执行任何写入。', 'CPClaw 09:28');
  const checklist = card(main, 'Configuration checklist', 54, 270, 590, 172, '基础配置检查', '配置完成后即可查询云枢业务数据');
  const items = [
    ['配置云枢登录地址、账号和密码', '去配置'],
    ['管理员同步应用、业务模型、表单、字段和 Action', '去初始化'],
    ['选择模型、API 地址和思考模式', '已保存']
  ];
  items.forEach((it, i) => {
    rect(checklist, `Row ${i}`, 12, 50 + i * 36, 566, 30, C.surface2, 6, C.lineSoft);
    text(checklist, `Item ${i}`, `${i + 1}. ${it[0]}`, 22, 57 + i * 36, 390, 12, C.text, FONT.regular, 18);
    chip(checklist, it[1], 486, 53 + i * 36, i === 2 ? 'green' : 'blue', 82);
  });
  button(main, '完成示例配置', 20, 890, 112, 'quiet');
  button(main, '查询我的销售订单数据', 140, 890, 156, 'quiet');
  button(main, '给这个客户新增跟进记录', 304, 890, 174, 'quiet');
  frame(main, 'Composer', 20, 938, 800, 58, C.surface, 6, C.line);
  rect(main, 'Upload entry', 32, 948, 38, 38, C.surface2, 6, '#AEB8C8');
  text(main, 'Upload plus', '+', 47, 956, 12, 16, C.muted, FONT.medium, 20);
  text(main, 'Composer placeholder', '输入自然语言问题，例如：查询我的销售订单数据', 82, 957, 520, 13, C.subtle, FONT.regular, 20);
  button(main, '发送', 744, 950, 62, 'primary');
  timeline(shell.right, 14, 84, 312, [
    { title: '等待用户输入', meta: '主输入框始终可见', color: C.subtle },
    { title: '基础配置待完成', meta: '账号、元数据初始化', color: '#D58A00' },
    { title: '写操作默认阻断', meta: '确认前不会执行真实写入', color: C.red }
  ]);
  metadataMatch(shell.right, 14, 286, 312, '销售管理 / 销售订单', 96, '字段：订单号、客户、金额、状态', 'green');
  metadataMatch(shell.right, 14, 380, 312, '客户管理 / 客户档案', 68, '用于详情和跟进记录上下文', 'amber');
  return shell.root;
}

function buildSettings(x, y) {
  const root = drawShell('02 设置面板 / 账号与模型配置', x, y).root;
  rect(root, 'Overlay', 0, 0, 1440, 1024, '#0F172A', 0).opacity = 0.36;
  const drawer = frame(root, 'Settings drawer', 620, 0, 820, 1024, C.surface, 0);
  text(drawer, 'Title', '设置与连接', 18, 18, 220, 18, C.text, FONT.bold, 24);
  button(drawer, '关闭', 736, 12, 64, 'secondary');
  line(drawer, 'Head line', 0, 58, 820, C.lineSoft);
  const tabs = ['云枢账号', '管理员元数据', '模型 API', '思考模式'];
  tabs.forEach((t, i) => {
    text(drawer, `Tab ${i}`, t, 18 + i * 96, 76, 86, 13, i === 0 ? C.blue : C.muted, i === 0 ? FONT.bold : FONT.regular, 18);
    if (i === 0) rect(drawer, 'Tab active', 18, 106, 56, 2, C.blue, 0);
  });
  input(drawer, '云枢登录地址', 'https://yunshu.example.com', 18, 132, 370);
  input(drawer, '租户 / 环境', 'prod-cn-east', 432, 132, 370);
  input(drawer, '账号', 'admin.operator', 18, 214, 370);
  input(drawer, '密码', '************', 432, 214, 370, true);
  input(drawer, '模型 Base URL', 'https://api.example.com/v1', 18, 296, 370);
  input(drawer, 'API Key', 'sk-********-saved', 432, 296, 370, true);
  input(drawer, '模型', 'gpt-5.5', 18, 378, 370);
  input(drawer, '思考模式', '平衡：解释充分，延迟可控', 432, 378, 370);
  text(drawer, 'Conn title', '连接测试', 18, 478, 16, C.text, FONT.bold, 22);
  chip(drawer, '上次成功 2026-06-20 18:42', 584, 478, 'green', 218);
  const result = frame(drawer, 'Connection result panel', 18, 516, 784, 136, C.surface2, 6, C.lineSoft);
  const rows = [
    ['云枢账号', '连接成功，权限：应用读取、模型读取、Action 需确认'],
    ['模型 API', '连通成功，Key 已保存，不回显明文'],
    ['失败状态', '无权限 / 连接失败会阻止执行，并引导重新测试']
  ];
  rows.forEach((r, i) => {
    text(result, `Key ${i}`, r[0], 14, 16 + i * 36, 90, 12, C.muted, FONT.regular, 18);
    text(result, `Value ${i}`, r[1], 116, 16 + i * 36, 620, 12, i === 2 ? C.red : C.text, FONT.regular, 18);
  });
  button(drawer, '测试连接', 608, 690, 90, 'secondary');
  button(drawer, '保存配置', 710, 690, 92, 'primary');
  return root;
}

function buildMetadata(x, y) {
  const root = frame(figma.currentPage, '03 元数据初始化 / 管理员同步', x, y, 1440, 1024, C.bg, 0);
  text(root, 'Title', '云枢元数据初始化', 40, 32, 300, 22, C.text, FONT.bold, 30);
  text(root, 'Desc', '同步应用、业务模型、表单、字段、Action，并写入检索索引。', 40, 66, 680, 13, C.muted, FONT.regular, 20);
  button(root, '开始同步', 1276, 36, 104, 'primary');
  const progress = frame(root, 'Sync progress', 40, 112, 1360, 96, C.surface, 6, C.lineSoft);
  text(progress, 'Progress title', '同步完成：应用、实体、字段、Action 已写入检索索引', 18, 18, 700, 14, C.text, FONT.bold, 20);
  chip(progress, '成功', 1280, 16, 'green', 58);
  rect(progress, 'Progress bg', 18, 60, 1320, 8, '#EDF2F7', 8);
  rect(progress, 'Progress fg', 18, 60, 1320, 8, C.blue, 8);
  stat(root, '应用数', '46', 40, 232, 320, 'blue');
  stat(root, '实体数', '318', 386, 232, 320, 'blue');
  stat(root, '索引文档数', '12,480', 732, 232, 320, 'blue');
  stat(root, '同步时间', '18:45', 1078, 232, 320, 'green');
  input(root, '检索应用、实体、字段', '销售订单', 40, 340, 420);
  table(root, 40, 430, 1360, [
    { label: '应用', w: 220 }, { label: '实体 / 表单', w: 300 }, { label: '字段', w: 160 }, { label: 'Action', w: 420 }, { label: '状态', w: 260 }
  ], [
    ['销售管理', '销售订单', '38', '提交审批、作废、导出', '已索引'],
    ['客户管理', '客户档案 / 跟进记录', '51', '新增跟进、转商机', '已索引'],
    ['合同管理', '销售合同', '44', '流程提交、盖章', '部分失败'],
    ['费用管理', '报销单 / 附件', '36', '提交、退回、附件上传', '已索引']
  ]);
  const warn = frame(root, 'Sync warning', 40, 652, 1360, 76, '#FFFAF0', 6, '#FFD99A');
  text(warn, 'Warning', '同步失败提示：合同管理 / 附件字段缺少读取权限，已跳过附件内容索引；审计中保留失败原因。', 18, 22, 1280, 14, C.amber, FONT.medium, 20);
  return root;
}

function buildQueryResult(x, y) {
  const shell = drawShell('04 工作台 / 查询销售订单结果', x, y);
  const main = shell.main;
  statusCard(main, 20, 77, 256, '云枢账号', '已连接，密码不回显', 'ok');
  statusCard(main, 564, 77, 256, '管理员元数据', '已同步 46 个应用', 'ok');
  message(main, 'user', 506, 174, 300, '查询我的销售订单数据', '你 09:32');
  const reply = frame(main, 'Assistant rich response', 24, 268, 780, 468, null, 0);
  rect(reply, 'Avatar', 0, 0, 22, 22, '#E8EDF3', 6);
  text(reply, 'Avatar label', 'AI', 5, 3, 14, 10, C.text, FONT.bold, 14);
  text(reply, 'Meta', 'CPClaw 09:32', 30, 1, 200, 12, C.muted, FONT.regular, 16);
  const bubble = frame(reply, 'Bubble', 30, 30, 750, 438, C.surface, 6, C.lineSoft);
  text(bubble, 'Intro', '已识别为只读查询。', 12, 12, 680, 14, C.text, FONT.bold, 20);
  card(bubble, 'Intent', 12, 48, 220, 66, '意图', '查询销售订单');
  card(bubble, 'Hit', 248, 48, 220, 66, '命中应用 / 实体', '销售管理 / 销售订单');
  const risk = card(bubble, 'Risk', 484, 48, 220, 66, '风险等级', '');
  chip(risk, '低风险', 12, 34, 'green', 66);
  const plan = frame(bubble, 'Execution plan', 12, 132, 692, 112, C.surface, 6, C.line);
  text(plan, 'Plan title', '执行计划', 12, 10, 120, 14, C.text, FONT.bold, 20);
  chip(plan, '可审计', 604, 9, 'blue', 70);
  text(plan, 'Plan text', '1. 按当前用户身份过滤“我的订单”。\n2. 检索订单号、客户、金额、状态、创建时间。\n3. 返回摘要、表格和 Agent Run ID。', 24, 42, 640, 13, C.text, FONT.regular, 20);
  const result = frame(bubble, 'Query result', 12, 260, 692, 160, C.surface, 6, C.line);
  text(result, 'Result title', '查询结果摘要', 12, 10, 140, 14, C.text, FONT.bold, 20);
  chip(result, '12 条记录', 594, 9, 'green', 82);
  text(result, 'Summary', '本月销售订单 12 条，总金额 2,386,400.00 元；3 条待审批，2 条存在逾期跟进。', 12, 40, 650, 13, C.text, FONT.regular, 20);
  table(result, 12, 72, 668, [
    { label: '订单号', w: 190 }, { label: '客户', w: 140 }, { label: '金额', w: 120 }, { label: '状态', w: 100 }, { label: '创建时间', w: 118 }
  ], [
    ['SO-2026-0618-009', '云栖制造', '¥428,000', '待审批', '06-18'],
    ['SO-2026-0617-014', '北辰能源', '¥196,500', '已确认', '06-17']
  ]);
  chip(bubble, 'AGT-RUN-20260620-8451', 12, 428, 'blue', 184);
  button(bubble, '查看审计', 212, 423, 84, 'quiet');
  timeline(shell.right, 14, 84, 312, [
    { title: '解析自然语言', meta: '查询意图，置信度 0.92', color: C.blue },
    { title: '匹配元数据', meta: '销售管理 / 销售订单', color: C.cyan },
    { title: '执行只读查询', meta: '耗时 1.42s，返回 12 条', color: C.green }
  ]);
  metadataMatch(shell.right, 14, 286, 312, '销售管理 / 销售订单', 96, '字段：订单号、客户、金额、状态', 'green');
  metadataMatch(shell.right, 14, 380, 312, '客户管理 / 客户档案', 68, '用于客户详情和跟进记录上下文', 'amber');
  return shell.root;
}

function buildRisk(x, y) {
  const shell = drawShell('05 工作台 / 高风险写操作确认', x, y, '风险确认与执行');
  const main = shell.main;
  statusCard(main, 20, 77, 256, '云枢账号', '已连接，密码不回显', 'ok');
  statusCard(main, 564, 77, 256, '管理员元数据', '已同步 46 个应用', 'ok');
  message(main, 'user', 358, 176, 448, '给这个客户新增跟进记录：明天下午电话确认合同条款', '你 09:35');
  const a = frame(main, 'Assistant write confirmation', 24, 292, 780, 394, null, 0);
  rect(a, 'Avatar', 0, 0, 22, 22, '#E8EDF3', 6);
  text(a, 'Avatar label', 'AI', 5, 3, 14, 10, C.text, FONT.bold, 14);
  text(a, 'Meta', 'CPClaw 09:35', 30, 1, 200, 12, C.muted, FONT.regular, 16);
  const b = frame(a, 'Bubble', 30, 30, 750, 362, C.surface, 6, C.lineSoft);
  text(b, 'Context', '我引用上一轮的第一条订单客户“云栖制造”。这是写操作，需要你确认后才会执行。', 12, 12, 704, 14, C.text, FONT.medium, 20);
  riskConfirmCard(b, 12, 54, 704, 'pending');
  timeline(shell.right, 14, 84, 312, [
    { title: '引用上下文', meta: '第一条订单 / 云栖制造', color: C.blue },
    { title: '识别写操作', meta: '新增客户跟进记录', color: C.red },
    { title: '等待用户确认', meta: '确认前不会执行真实写入', color: '#D58A00' }
  ]);
  riskConfirmCard(shell.right, 14, 298, 312, 'pending');
  return shell.root;
}

function buildAudit(x, y) {
  const root = frame(figma.currentPage, '06 审计详情 / Agent Run', x, y, 1440, 1024, C.bg, 0);
  text(root, 'Title', 'Agent Run 审计详情', 40, 32, 340, 22, C.text, FONT.bold, 30);
  input(root, 'Agent Run ID 查询', 'AGT-RUN-20260620-8451', 960, 28, 300);
  button(root, '查询', 1280, 50, 80, 'primary');
  stat(root, '意图', '查询销售订单', 40, 126, 320, 'blue');
  stat(root, '风险等级', '低风险', 386, 126, 320, 'green');
  stat(root, '状态', '成功', 732, 126, 320, 'green');
  stat(root, '耗时', '1.42s', 1078, 126, 320, 'default');
  const plan = card(root, 'Audit execution plan', 40, 240, 640, 230, '执行计划', '只读查询，可审计');
  text(plan, 'Plan', '1. 将“我的销售订单数据”解析为当前用户负责订单查询。\n2. 匹配销售管理应用、销售订单实体及客户关联字段。\n3. 调用云枢查询接口，仅返回脱敏摘要和必要字段。', 18, 64, 590, 14, C.text, FONT.regular, 22);
  const log = card(root, 'Tool call logs', 720, 240, 680, 330, '工具调用记录', '输入输出已脱敏展示');
  const rows = [
    ['metadata.search', '{ query: "销售订单", user: "u_***" }', '200'],
    ['yunshu.query', '{ app: "sales", entity: "order", filter: "owner=current" }', '200'],
    ['audit.write', '{ runId: "AGT-RUN-20260620-8451", risk: "low" }', '200']
  ];
  rows.forEach((r, i) => {
    text(log, `tool ${i}`, r[0], 18, 66 + i * 54, 120, 12, C.text, FONT.medium, 18);
    text(log, `payload ${i}`, r[1], 150, 66 + i * 54, 390, 12, C.muted, FONT.regular, 18);
    chip(log, r[2], 586, 62 + i * 54, 'green', 50);
    line(log, `line ${i}`, 18, 104 + i * 54, 640, C.lineSoft);
  });
  const mask = card(root, 'Redaction detail', 40, 514, 640, 136, '脱敏规则', '密码、Token、API Key、手机号、附件名均不展示明文');
  chip(mask, 'password: ********', 18, 64, 'neutral', 150);
  chip(mask, 'token: sk-********', 184, 64, 'neutral', 140);
  chip(mask, 'user: u_***', 340, 64, 'neutral', 100);
  return root;
}

function navPill(parent, label, x, y, w, active = false) {
  const f = frame(parent, `Top nav / ${label}`, x, y, w, 36, active ? C.blueSoft : C.surface, 6, active ? '#C7D7FF' : C.lineSoft);
  rect(f, 'Icon', 10, 10, 14, 14, active ? C.blue : C.subtle, 4);
  text(f, 'Label', label, 32, 8, w - 42, 13, active ? C.blue : C.text, FONT.medium, 18);
  return f;
}

function compactConversation(parent, x, y, w, titleValue, meta, active = false) {
  const f = frame(parent, `Round source / ${titleValue}`, x, y, w, 64, active ? C.blueSoft : C.surface, 6, active ? '#C7D7FF' : C.lineSoft);
  text(f, 'Title', titleValue, 12, 10, w - 24, 13, active ? C.blue : C.text, FONT.medium, 18);
  text(f, 'Meta', meta, 12, 34, w - 24, 12, C.muted, FONT.regular, 16);
  return f;
}

function tracePanelStep(parent, x, y, w, titleValue, meta, tone = 'blue') {
  const colors = { blue: C.blue, cyan: C.cyan, green: C.green, amber: C.amber, red: C.red };
  const f = frame(parent, `Trace step / ${titleValue}`, x, y, w, 68, C.surface, 6, C.lineSoft);
  rect(f, 'Dot', 12, 18, 12, 12, colors[tone] || C.blue, 6);
  text(f, 'Title', titleValue, 36, 12, w - 48, 13, C.text, FONT.bold, 18);
  text(f, 'Meta', meta, 36, 36, w - 48, 12, C.muted, FONT.regular, 16);
  return f;
}

function topNavShell(page, name, x, y, w = 1440, h = 1024) {
  const root = frame(page, name, x, y, w, h, C.bg, 0);
  rect(root, 'Top nav bg', 0, 0, w, 64, C.surface, 0);
  line(root, 'Top nav line', 0, 64, w, C.lineSoft);
  rect(root, 'Logo mark', 24, 17, 30, 30, C.navy, 6);
  text(root, 'Logo label', 'CP', 31, 23, 18, 11, '#FFFFFF', FONT.bold, 14);
  text(root, 'Brand', 'CPClaw', 64, 14, 116, 15, C.text, FONT.bold, 21);
  text(root, 'Brand sub', '云枢超级智能体', 64, 36, 148, 12, C.muted, FONT.regular, 16);
  navPill(root, '对话', 244, 14, 92, true);
  navPill(root, '元数据', 346, 14, 106, false);
  navPill(root, '审计', 462, 14, 92, false);
  navPill(root, '账号与模型', 564, 14, 128, false);
  chip(root, 'MySQL 已连接', w - 424, 20, 'green', 112);
  chip(root, '模型 AuthineAI', w - 300, 20, 'blue', 118);
  button(root, '新建会话', w - 170, 15, 98, 'secondary');
  button(root, '设置', w - 64, 15, 44, 'primary');
  return root;
}

function buildLayoutProposal(page) {
  const root = topNavShell(page, '布局调整提案 / 顶部导航 + 右侧执行过程', 0, 0, 1440, 1024);
  const bodyY = 64;
  const bodyH = 960;
  const history = frame(root, 'Left / 会话列表，仅保留业务上下文', 24, bodyY + 20, 220, bodyH - 40, C.surface, 6, C.lineSoft);
  text(history, 'Title', '历史会话', 14, 14, 90, 14, C.text, FONT.bold, 20);
  button(history, '刷新', 152, 9, 54, 'quiet');
  button(history, '+ 新建', 14, 50, 192, 'primary');
  compactConversation(history, 14, 100, 192, '商机数量与阶段', '刚刚 · 2 轮 · Run 9018', true);
  compactConversation(history, 14, 174, 192, '客户年度分析', '16:48 · 3 轮', false);
  compactConversation(history, 14, 248, 192, '项目基础数据商机', '15:20 · 同名对象', false);
  compactConversation(history, 14, 322, 192, '线索转化分析', '昨天 · 待复核', false);
  line(history, 'History bottom line', 14, 820, 192, C.lineSoft);
  text(history, 'Tip', '全局菜单已移到顶部；左侧只保留会话切换，减少宽度占用。', 14, 842, 190, 12, C.muted, FONT.regular, 18);

  const chat = frame(root, 'Center / 主对话窗口，最大化业务阅读区', 260, bodyY + 20, 824, bodyH - 40, C.surface, 6, C.lineSoft);
  rect(chat, 'Chat toolbar bg', 0, 0, 824, 58, C.surface, 6);
  line(chat, 'Chat toolbar line', 0, 58, 824, C.lineSoft);
  text(chat, 'Chat title', '商机数量与阶段', 18, 14, 240, 16, C.text, FONT.bold, 22);
  chip(chat, '会话 cf7ea0cc', 264, 16, 'neutral', 112);
  chip(chat, '思考关闭', 620, 16, 'neutral', 76);
  button(chat, '上传', 704, 12, 54, 'quiet');
  button(chat, '发送', 766, 12, 44, 'primary');

  const user1 = frame(chat, 'Message / user / count opportunity', 466, 94, 336, 76, '#E8F0FF', 6, '#C7D7FF');
  text(user1, 'Role', '你 17:10', 12, 10, 120, 12, C.muted, FONT.medium, 16);
  text(user1, 'Body', '系统有多少商机？', 12, 36, 286, 15, C.text, FONT.regular, 22);
  const ai1 = frame(chat, 'Message / assistant / count result', 22, 202, 672, 132, '#F7F9FC', 6, C.lineSoft);
  text(ai1, 'Role', 'CPClaw 17:10', 14, 12, 140, 12, C.muted, FONT.medium, 16);
  chip(ai1, '过程 Run 9017', 538, 10, 'blue', 112);
  text(ai1, 'Body', '已在云枢中查询到“商机”对应数据，总计 **237** 条。', 14, 42, 610, 15, C.text, FONT.regular, 22);
  text(ai1, 'Trace hint', '点击“过程”后，右侧展示本轮 Observe / Think / Act / Reflect。', 14, 84, 520, 12, C.muted, FONT.regular, 18);

  const user2 = frame(chat, 'Message / user / follow stage', 516, 370, 286, 76, '#E8F0FF', 6, '#C7D7FF');
  text(user2, 'Role', '你 17:11', 12, 10, 120, 12, C.muted, FONT.medium, 16);
  text(user2, 'Body', '分别在什么阶段？', 12, 36, 240, 15, C.text, FONT.regular, 22);
  const ai2 = frame(chat, 'Message / assistant / stage result selected', 22, 478, 720, 260, '#F7F9FC', 6, C.blue);
  text(ai2, 'Role', 'CPClaw 17:12', 14, 12, 140, 12, C.muted, FONT.medium, 16);
  chip(ai2, '当前过程 Run 9018', 558, 10, 'blue', 142);
  text(ai2, 'Body', '已在云枢中查询到“商机”对应数据，总计 **237** 条。', 14, 42, 650, 15, C.text, FONT.regular, 22);
  text(ai2, 'Sub title', '按阶段分布', 14, 82, 160, 14, C.text, FONT.bold, 20);
  table(ai2, 14, 112, 692, [{ label: '阶段', w: 250 }, { label: '数量', w: 140 }, { label: '说明', w: 302 }], [
    ['未签约', '135', '当前最大阶段池'],
    ['暂停', '13', '需后续跟进'],
    ['已签约', '65', '已转化'],
    ['停止 / 丢单', '24', '需复盘原因']
  ]);

  const composer = frame(chat, 'Composer / sticky input', 18, 824, 788, 76, C.surface, 6, C.line);
  rect(composer, 'Attachment icon', 14, 18, 38, 38, C.surface2, 6, '#AEB8C8');
  text(composer, 'Input placeholder', '继续追问，例如：按负责人分别是多少？', 66, 27, 520, 13, C.subtle, FONT.regular, 20);
  button(composer, '发送', 710, 21, 58, 'primary');

  const trace = frame(root, 'Right / 关联当前回复的后端处理流程', 1100, bodyY + 20, 316, bodyH - 40, C.surface, 6, C.lineSoft);
  text(trace, 'Title', '后端处理流程', 16, 14, 160, 16, C.text, FONT.bold, 22);
  chip(trace, '关联当前回复', 190, 12, 'blue', 108);
  text(trace, 'Desc', '点击任一助手消息的“过程”标签，右侧切换到该轮执行记录。', 16, 44, 270, 12, C.muted, FONT.regular, 18);
  line(trace, 'Head line', 0, 76, 316, C.lineSoft);
  text(trace, 'Round title', '第 2 轮：阶段分布追问', 16, 96, 230, 14, C.text, FONT.bold, 20);
  chip(trace, 'Run 9018', 232, 94, 'neutral', 68);
  compactConversation(trace, 16, 130, 284, '上一轮对象', '商机 / int_bu_oppor / total=237', true);
  tracePanelStep(trace, 16, 218, 284, 'Observe 观察上下文', '继承对象=是；有效目标包含 int_bu_oppor', 'blue');
  tracePanelStep(trace, 16, 294, 284, 'Think 理解意图', 'analyze_data；维度=阶段/状态', 'cyan');
  tracePanelStep(trace, 16, 370, 284, 'Act 执行动作', '调用真实云枢运行态，聚合 237 条商机', 'green');
  tracePanelStep(trace, 16, 446, 284, 'Reflect 反思检查', '对象未串线；无需用户补充', 'green');
  const data = frame(trace, 'Trace data summary', 16, 544, 284, 160, C.surface2, 6, C.lineSoft);
  text(data, 'Title', '数据摘要', 12, 12, 120, 14, C.text, FONT.bold, 20);
  text(data, 'Summary', 'schemaCode: int_bu_oppor\nsource: 云枢运行态\ntotal: 237\n分布: 未签约135 / 暂停13 / 已签约65 / 停止20 / 丢单4', 12, 44, 250, 12, C.text, FONT.regular, 18);
  const perf = frame(trace, 'Trace performance note', 16, 724, 284, 108, '#FFF4DF', 6, '#FFD99A');
  text(perf, 'Title', '性能提示', 12, 12, 120, 13, C.amber, FONT.bold, 18);
  text(perf, 'Body', '阶段字段不足时需要补运行态详情；后续用字段级元数据或聚合接口优化。', 12, 42, 250, 12, C.amber, FONT.regular, 18);
  button(trace, '打开审计详情', 16, 858, 128, 'secondary');
  button(trace, '复制 Run ID', 154, 858, 104, 'quiet');

  const notes = frame(page, '设计说明 / 布局调整确认点', 0, 1080, 1440, 280, C.surface, 0, C.lineSoft);
  text(notes, 'Title', '布局调整确认点', 32, 28, 220, 22, C.text, FONT.bold, 30);
  const noteItems = [
    '1. 全局菜单从左侧移到顶部导航，对话页可横向释放 220px 左右空间。',
    '2. 左侧只保留历史会话，可后续做折叠，不再承载元数据/审计/设置入口。',
    '3. 中央对话窗口只展示业务回答，消息内用“过程 Run”标签关联右侧流程。',
    '4. 右侧流程面板默认显示当前选中的助手回复，可切换每一轮 Observe/Think/Act/Reflect。',
    '5. 移动端建议把右侧流程改为底部抽屉，避免压缩对话窗口。'
  ];
  noteItems.forEach((item, i) => text(notes, `Note ${i}`, item, 32, 82 + i * 34, 1060, 14, C.text, FONT.regular, 22));
  return root;
}

function buildComponentsPage(page) {
  const root = frame(page, '组件规范页 / CPClaw Design System', 0, 0, 1440, 1500, C.bg, 0);
  text(root, 'Title', 'CPClaw 组件规范页', 40, 34, 360, 24, C.text, FONT.bold, 32);
  text(root, 'Desc', '企业级、安静、清晰、可信、可审计。圆角控制在 8px 以内，状态色清晰区分风险。', 40, 72, 900, 14, C.muted, FONT.regular, 22);
  const tokens = card(root, 'Tokens', 40, 124, 1360, 150, '视觉 Tokens', '颜色、圆角、边框、文本密度');
  const swatches = [
    ['bg', C.bg], ['surface', C.surface], ['text', C.text], ['blue', C.blue], ['green', C.green], ['amber', C.amber], ['red', C.red], ['navy', C.navy]
  ];
  swatches.forEach((s, i) => {
    rect(tokens, `Swatch ${s[0]}`, 18 + i * 158, 62, 42, 42, s[1], 6, C.line);
    text(tokens, `Swatch label ${s[0]}`, s[0], 68 + i * 158, 72, 90, 12, C.muted, FONT.regular, 18);
  });
  const y = 310;
  const c1 = card(root, 'Session item component', 40, y, 320, 170, '会话列表项', '标题、时间、审计摘要');
  conversationItem(c1, 18, 68, 284, '销售订单查询与跟进', '刚刚  ·  Run 8451', true);
  const c2 = card(root, 'Message bubbles', 386, y, 320, 170, '消息气泡', '用户 / 助手');
  message(c2, 'assistant', 18, 58, 260, '已识别为只读查询。', 'CPClaw');
  message(c2, 'user', 70, 112, 210, '查询我的销售订单', '你');
  const c3 = card(root, 'Markdown table', 732, y, 320, 170, 'Markdown / 表格结果', '摘要 + 结构化表格');
  table(c3, 18, 62, 284, [{ label: '订单号', w: 110 }, { label: '客户', w: 84 }, { label: '状态', w: 90 }], [['SO-009', '云栖制造', '待审批'], ['SO-014', '北辰能源', '已确认']]);
  const c4 = card(root, 'Timeline component', 1078, y, 320, 210, 'Agent 执行时间线', '解析、匹配、执行、审计');
  timeline(c4, 18, 58, 284, [
    { title: '解析自然语言', meta: '0.92', color: C.blue },
    { title: '匹配元数据', meta: '销售订单', color: C.cyan },
    { title: '执行查询', meta: '1.42s', color: C.green }
  ]);
  const y2 = 560;
  const c5 = card(root, 'Metadata match component', 40, y2, 320, 180, '元数据匹配卡片', '置信度 + 命中字段');
  metadataMatch(c5, 18, 62, 284, '销售管理 / 销售订单', 96, '订单号、客户、金额、状态', 'green');
  const c6 = card(root, 'Candidate selector', 386, y2, 320, 180, '候选应用 / 实体选择器', '多候选需要用户选择');
  ['销售订单', '采购订单', '合同订单'].forEach((v, i) => {
    const row = frame(c6, `Candidate ${i}`, 18, 60 + i * 36, 284, 30, C.surface, 6, C.lineSoft);
    text(row, 'Name', v, 10, 7, 140, 12, C.text, FONT.medium, 18);
    chip(row, i === 0 ? '选择' : '候选', 218, 3, i === 0 ? 'blue' : 'neutral', 54);
  });
  const c7 = card(root, 'Attachment uploader', 732, y2, 320, 180, '附件上传入口', '上传前进入风险确认');
  rect(c7, 'Uploader', 18, 62, 284, 82, C.surface2, 6, '#AEB8C8');
  text(c7, 'Uploader text', '+ 上传附件或拖入文件\n附件上传属于高风险操作', 44, 84, 230, 13, C.muted, FONT.medium, 20);
  const c8 = card(root, 'Connection status', 1078, y2, 320, 180, '连接测试结果状态', '成功、失败、无权限');
  chip(c8, '成功', 18, 70, 'green', 60);
  chip(c8, '连接失败', 90, 70, 'red', 82);
  chip(c8, '无权限', 184, 70, 'amber', 72);
  const y3 = 800;
  const c9 = card(root, 'Settings form', 40, y3, 666, 270, '设置表单', '密码、Token、API Key 不回显明文');
  input(c9, '云枢登录地址', 'https://yunshu.example.com', 18, 66, 290);
  input(c9, '账号', 'admin.operator', 334, 66, 290);
  input(c9, '密码', '************', 18, 148, 290, true);
  input(c9, 'API Key', 'sk-********-saved', 334, 148, 290, true);
  const c10 = card(root, 'High risk card variants', 732, y3, 666, 390, '高风险确认卡片', '待确认、已确认、已取消');
  riskConfirmCard(c10, 18, 62, 300, 'pending');
  riskConfirmCard(c10, 338, 62, 300, 'confirmed');
  riskConfirmCard(c10, 338, 220, 300, 'cancelled');
  return root;
}

function buildStatesPage(page) {
  const root = frame(page, '主要状态页 / Core States', 0, 0, 1440, 1280, C.bg, 0);
  text(root, 'Title', '主要状态页', 40, 34, 260, 24, C.text, FONT.bold, 32);
  text(root, 'Desc', '每个核心界面覆盖空、加载、成功、失败、无权限、待确认、已确认、已取消、无匹配、多候选。', 40, 72, 980, 14, C.muted, FONT.regular, 22);
  const states = [
    ['空状态', '尚无历史会话，主输入框保持可见', 'neutral'],
    ['加载中', '时间线逐步展示解析、匹配、执行', 'blue'],
    ['成功', '返回摘要、表格和审计编号', 'green'],
    ['失败', '展示失败原因、重试和审计入口', 'red'],
    ['无权限 / 连接失败', '阻止执行，提示重新测试连接', 'red'],
    ['待用户确认', '写操作确认前不会执行真实写入', 'amber'],
    ['已确认', '生成写入审计编号和结果状态', 'green'],
    ['已取消', '明确未执行任何真实写入', 'amber'],
    ['无匹配结果', '建议同步元数据或调整查询描述', 'neutral'],
    ['多候选需要选择', '用户选择应用 / 实体后再继续执行', 'blue'],
    ['同步失败', '展示失败应用、实体、字段和权限原因', 'red'],
    ['附件上传待确认', '附件上传入口触发高风险确认', 'amber']
  ];
  states.forEach((s, i) => {
    const col = i % 4;
    const row = Math.floor(i / 4);
    const x = 40 + col * 346;
    const y = 130 + row * 210;
    const tile = card(root, `State / ${s[0]}`, x, y, 320, 168, s[0], s[1]);
    chip(tile, s[2] === 'neutral' ? '状态' : s[2] === 'blue' ? '进行中' : s[2] === 'green' ? '成功' : s[2] === 'red' ? '失败' : '等待', 18, 108, s[2], 82);
  });
  return root;
}

function buildNarrowPage(page) {
  const root = frame(page, '窄屏适配方案 / 390px', 0, 0, 1440, 1024, C.bg, 0);
  text(root, 'Title', '窄屏适配方案', 40, 34, 300, 24, C.text, FONT.bold, 32);
  text(root, 'Desc', '隐藏左侧会话栏和右侧审计栏，Agent 过程、元数据匹配、确认卡片改为底部抽屉；输入框始终可见。', 40, 72, 1040, 14, C.muted, FONT.regular, 22);
  const phone = frame(root, 'Mobile / Chat workspace', 40, 128, 390, 844, C.surface, 6, C.line);
  rect(phone, 'Top', 0, 0, 390, 54, C.surface, 6);
  line(phone, 'Top line', 0, 54, 390, C.lineSoft);
  button(phone, '☰', 12, 10, 38, 'secondary');
  text(phone, 'Brand', 'CPClaw', 160, 16, 90, 16, C.text, FONT.bold, 22);
  button(phone, '#', 340, 10, 38, 'secondary');
  rect(phone, 'Body bg', 0, 55, 390, 717, C.bg, 0);
  message(phone, 'assistant', 12, 78, 340, '已匹配销售订单，准备执行只读查询。', 'CPClaw');
  message(phone, 'user', 86, 166, 290, '查询我的销售订单数据', '你');
  const res = frame(phone, 'Mobile result', 12, 258, 366, 204, C.surface, 6, C.lineSoft);
  text(res, 'Title', '结果摘要', 12, 12, 120, 14, C.text, FONT.bold, 20);
  chip(res, '只读', 304, 10, 'green', 50);
  text(res, 'Summary', '返回 12 条订单，3 条待审批，2 条逾期跟进。', 12, 44, 320, 13, C.text, FONT.regular, 20);
  table(res, 12, 88, 342, [{ label: '订单号', w: 140 }, { label: '客户', w: 100 }, { label: '状态', w: 102 }], [['SO-009', '云栖制造', '待审批'], ['SO-014', '北辰能源', '已确认']]);
  button(phone, '展开执行过程', 12, 482, 132, 'quiet');
  rect(phone, 'Composer bg', 0, 772, 390, 72, C.surface, 0);
  line(phone, 'Composer line', 0, 772, 390, C.lineSoft);
  rect(phone, 'Upload', 12, 789, 38, 38, C.surface2, 6, '#AEB8C8');
  frame(phone, 'Input', 60, 789, 244, 38, C.surface, 6, C.line);
  text(phone, 'Placeholder', '继续追问', 72, 798, 180, 13, C.subtle, FONT.regular, 20);
  button(phone, '发送', 314, 789, 62, 'primary');
  const drawer = frame(root, 'Mobile / Bottom drawer', 480, 324, 390, 520, C.surface, 6, C.line);
  text(drawer, 'Title', 'Agent 执行过程', 18, 18, 180, 16, C.text, FONT.bold, 22);
  timeline(drawer, 18, 62, 354, [
    { title: '解析自然语言', meta: '查询意图，置信度 0.92', color: C.blue },
    { title: '匹配元数据', meta: '销售管理 / 销售订单', color: C.cyan },
    { title: '执行只读查询', meta: '返回 12 条记录', color: C.green }
  ]);
  metadataMatch(drawer, 18, 260, 354, '销售管理 / 销售订单', 96, '字段：订单号、客户、金额、状态', 'green');
  const confirm = frame(root, 'Mobile / Confirm drawer', 920, 228, 390, 616, C.surface, 6, C.line);
  text(confirm, 'Title', '高风险操作确认', 18, 18, 180, 16, C.text, FONT.bold, 22);
  riskConfirmCard(confirm, 18, 62, 354, 'pending');
  return root;
}

function connect(source, target) {
  try {
    source.reactions = [{
      trigger: { type: 'ON_CLICK' },
      action: {
        type: 'NODE',
        destinationId: target.id,
        navigation: 'NAVIGATE',
        transition: { type: 'SMART_ANIMATE', easing: { type: 'EASE_OUT' }, duration: 0.2 },
        preserveScrollPosition: false
      }
    }];
  } catch (err) {
    safeReactions.push(`${source.name} -> ${target.name}`);
  }
}

async function main() {
  await Promise.all([figma.loadFontAsync(FONT.regular), figma.loadFontAsync(FONT.medium), figma.loadFontAsync(FONT.bold)]);
  const pageNames = ['01 主流程原型', '02 组件规范', '03 主要状态', '04 窄屏适配', '05 布局调整提案'];
  const pages = {};
  for (const name of pageNames) {
    let p = figma.root.children.find(page => page.name === name);
    if (!p) p = figma.createPage();
    p.name = name;
    clearPage(p);
    pages[name] = p;
  }
  figma.currentPage = pages['01 主流程原型'];
  const first = buildWorkspaceEmpty(0, 0);
  const settings = buildSettings(1520, 0);
  const metadata = buildMetadata(3040, 0);
  const query = buildQueryResult(0, 1160);
  const risk = buildRisk(1520, 1160);
  const audit = buildAudit(3040, 1160);
  connect(first.findOne(n => n.name === 'Button / 配置连接'), settings);
  connect(first.findOne(n => n.name === 'Button / 初始化'), metadata);
  connect(settings.findOne(n => n.name === 'Button / 保存配置'), metadata);
  connect(metadata.findOne(n => n.name === 'Button / 开始同步'), query);
  connect(query.findOne(n => n.name === 'Button / 查看审计'), audit);
  connect(query.findOne(n => n.name === 'Button / 发送'), risk);
  connect(risk.findOne(n => n.name === 'Button / 确认执行'), audit);
  pages['01 主流程原型'].prototypeStartNode = first;

  figma.currentPage = pages['02 组件规范'];
  buildComponentsPage(pages['02 组件规范']);
  figma.currentPage = pages['03 主要状态'];
  buildStatesPage(pages['03 主要状态']);
  figma.currentPage = pages['04 窄屏适配'];
  buildNarrowPage(pages['04 窄屏适配']);
  figma.currentPage = pages['05 布局调整提案'];
  buildLayoutProposal(pages['05 布局调整提案']);

  figma.currentPage = pages['01 主流程原型'];
  figma.viewport.scrollAndZoomIntoView([first]);
  const message = safeReactions.length
    ? `CPClaw prototype generated. Some prototype links may need manual wiring: ${safeReactions.length}.`
    : 'CPClaw prototype generated with pages, components, states, and clickable flow.';
  figma.closePlugin(message);
}

main().catch(err => {
  figma.closePlugin(`CPClaw generator failed: ${err.message}`);
});
