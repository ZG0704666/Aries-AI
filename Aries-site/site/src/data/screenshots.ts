import home from '../assets/screenshots/home.jpg';
import autoExecute from '../assets/screenshots/auto-execute.jpg';
import chat from '../assets/screenshots/chat.jpg';

export interface ScreenshotSlot {
  id: string;
  label: string;
  caption: string;
  src?: string;
  alt?: string;
}

/**
 * 首页三图位。统一从 Astro assets 读取，构建时会生成带哈希的静态资源。
 */
export const screenshots: ScreenshotSlot[] = [
  {
    id: 'auto-execute',
    label: 'Auto Execute',
    caption: '识别需求后自动执行',
    src: autoExecute.src,
    alt: 'Aries AI 自动化任务执行界面',
  },
  {
    id: 'home',
    label: 'Aries 主界面',
    caption: '统一入口，随时开始对话',
    src: home.src,
    alt: 'Aries AI 手机助手主界面',
  },
  {
    id: 'chat',
    label: '普通问答',
    caption: '无需操作时直接回答',
    src: chat.src,
    alt: 'Aries AI 普通问答界面',
  },
];
