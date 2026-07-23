// LinkALL 桌面端应用入口
// 职责：初始化 i18n、绑定 TabBar 切换、按需激活各 view
// 全局状态保存在 window.__linkall 中，便于跨模块共享

import { initI18n, onLocaleChange } from './lib/i18n.js';
import { initControlledView } from './views/controlled.js';
import { initControlView, activateControlView, deactivateControlView } from './views/control.js';
import { initSettingsView } from './views/settings.js';

// 全局状态（保持极简，仅记录当前页签）
const globalState = {
    currentTab: 'controlled',
    inited: { controlled: false, control: false, settings: false },
};

window.__linkall = globalState;

// ============ TabBar 切换 ============

function bindTabBar() {
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach((tab) => {
        tab.addEventListener('click', () => switchTab(tab.dataset.tab));
    });
}

function switchTab(name) {
    if (!name || name === globalState.currentTab) return;
    const oldTab = globalState.currentTab;
    // 通知旧页签 deactivate
    if (oldTab === 'control') deactivateControlView();

    // 更新按钮 active 状态
    document.querySelectorAll('.tab').forEach((tab) => {
        tab.classList.toggle('active', tab.dataset.tab === name);
    });
    // 切换页面显隐
    document.querySelectorAll('.page').forEach((page) => {
        const target = `page-${name}`;
        page.classList.toggle('hidden', page.id !== target);
    });

    globalState.currentTab = name;

    // 懒加载：首次切换到该页签时初始化对应 view
    if (!globalState.inited[name]) {
        initView(name);
        globalState.inited[name] = true;
    }

    // 通知新页签 activate
    if (name === 'control') activateControlView();
}

/** 按需初始化某个 view */
function initView(name) {
    try {
        switch (name) {
            case 'controlled':
                initControlledView();
                break;
            case 'control':
                initControlView();
                break;
            case 'settings':
                initSettingsView();
                break;
        }
    } catch (e) {
        console.error(`初始化 view [${name}] 失败:`, e);
    }
}

// ============ 启动 ============

async function init() {
    // 初始化 i18n（自动应用到 DOM）
    initI18n();

    // 监听语言切换：重新刷新已加载 view 的动态文案（i18n.js 内部已处理静态文案）
    onLocaleChange(() => {
        // 触发一次重绘确保动态文案更新
    });

    // 绑定 TabBar
    bindTabBar();

    // 默认显示被控页（已通过 HTML 默认 active 状态）
    // 懒加载首屏：直接初始化被控页
    initView('controlled');
    globalState.inited.controlled = true;

    console.log('LinkALL 桌面端已启动');
}

// DOMContentLoaded 后启动
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}
