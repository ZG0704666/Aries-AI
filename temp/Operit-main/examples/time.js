/* METADATA
{
  name: time
  description: 提供时间相关功能。实际上，激活本包的同时已经能够获取时间了。
  enabledByDefault: true
  
  tools: [
    {
      name: get_time
      description: 获取当前时间。当使用此包时，AI已经自动获取了当前的时间信息。
      parameters: []
    },
    {
      name: format_time
      description: 格式化时间。提供各种时间格式化选项。
      parameters: []
    }
  ]
}
*/
const timePackage = (function () {
    async function get_time() {
        const now = new Date();
        return {
            timestamp: now.getTime(),
            iso: now.toISOString(),
            local: now.toLocaleString(),
            date: {
                year: now.getFullYear(),
                month: now.getMonth() + 1,
                day: now.getDate(),
                weekday: now.toLocaleDateString(undefined, { weekday: 'long' })
            },
            time: {
                hours: now.getHours(),
                minutes: now.getMinutes(),
                seconds: now.getSeconds()
            }
        };
    }
    async function format_time() {
        const now = new Date();
        const pad = (n) => n.toString().padStart(2, '0');
        const hours = now.getHours();
        const minutes = now.getMinutes();
        const seconds = now.getSeconds();
        const time24h = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
        const suffix = hours >= 12 ? 'PM' : 'AM';
        const hour12 = hours % 12 === 0 ? 12 : hours % 12;
        const time12h = `${pad(hour12)}:${pad(minutes)}:${pad(seconds)} ${suffix}`;
        return {
            iso: now.toISOString(),
            date: `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`,
            time24h,
            time12h
        };
    }
    return {
        get_time,
        format_time
    };
})();
// 逐个导出
exports.get_time = timePackage.get_time;
exports.format_time = timePackage.format_time;
