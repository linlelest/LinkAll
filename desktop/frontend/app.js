// LinkALL 桌面端应用入口
// 职责：初始化 i18n、绑定 TabBar 切换、按需激活各 view
// 首次启动时先走 setup 引导（语言/服务器/登录），完成后才进入主界面。
// 全局状态保存在 window.__linkall 中，便于跨模块共享

import { initI18n, onLocaleChange } from './lib/i18n.js';
import { initControlledView } from './views/controlled.js';
import { initControlView, activateControlView, deactivateControlView } from './views/control.js';
import { initSettingsView } from './views/settings.js';
import { startSetup, isSetupDone } from './views/setup.js';
import { api } from './lib/api.js';

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

    // 绑定 TabBar（引导覆盖层显示时会被遮挡，但仍可提前绑定）
    bindTabBar();

    // 检查引导与登录状态，决定是否进入引导界面
    const setupDone = isSetupDone();
    let loggedIn = false;
    try {
        loggedIn = await api.getAuthStatus();
    } catch (e) {
        // 后端未就绪 / 数据库未初始化时按未登录处理，走引导流程
        console.warn('获取登录状态失败，按未登录处理:', e);
    }

    if (!setupDone || !loggedIn) {
        // 需要引导：首次启动走完整三步；已完成但未登录则直接跳到登录步
        startSetup({
            startStep: !setupDone ? 1 : 3,
            onComplete: enterMainApp,
        });
        console.log('LinkALL 桌面端启动：进入引导界面');
        return;
    }

    // 已完成引导且已登录，直接进入主界面
    enterMainApp();
}

/** 进入主界面：初始化被控页首屏 */
function enterMainApp() {
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
