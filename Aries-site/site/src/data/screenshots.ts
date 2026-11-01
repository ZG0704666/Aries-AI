export interface ScreenshotSlot {
  id: string;
  label: string;
  caption: string;
  src?: string;
  alt?: string;
}

/**
 * 首页三图位。截图未就绪时留空，组件会渲染品牌占位屏。
 * 后续只需把图片放进 src/assets/screenshots/ 并填写 src 即可。
 */
export const screenshots: ScreenshotSlot[] = [
  {
    id: 'chat',
    label: '主对话',
    caption: '一句话下达任务',
  },
  {
    id: 'running',
    label: '自动执行',
    caption: '跨应用完成操作',
  },
  {
    id: 'voice',
    label: '语音输入',
    caption: '本地离线识别',
  },
];
