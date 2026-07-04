/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.core.tools.extended

/**
 * 扩展应用包名映射
 * 包含 250+ 常用应用的包名映射
 */
object ExtendedAppMapping {

    /**
     * 社交通讯应用 (35个)
     */
    val SOCIAL_COMMUNICATION = mapOf(
        // 微信生态
        "微信" to "com.tencent.mm",
        "wechat" to "com.tencent.mm",
        "微信读书" to "com.tencent.weread",
        "微信运动" to "com.tencent.mm",
        "微信支付" to "com.tencent.mm",
        
        // QQ 生态
        "QQ" to "com.tencent.mobileqq",
        "qq" to "com.tencent.mobileqq",
        "QQ邮箱" to "com.tencent.androidqqmail",
        "QQ浏览器" to "com.tencent.mtt",
        "QQ音乐" to "com.tencent.qqmusic",
        "QQ游戏" to "com.tencent.qqgame",
        "腾讯会议" to "com.tencent.wemeet",
        "企业微信" to "com.tencent.wework",
        "wework" to "com.tencent.wework",
        
        // 新浪微博
        "微博" to "com.sina.weibo",
        "新浪微博" to "com.sina.weibo",
        "weibo" to "com.sina.weibo",
        
        // 字节跳动
        "抖音" to "com.ss.android.ugc.aweme",
        "douyin" to "com.ss.android.ugc.aweme",
        "今日头条" to "com.ss.android.article.news",
        "toutiao" to "com.ss.android.article.news",
        "西瓜视频" to "com.ss.android.article.video",
        "飞书" to "com.ss.android.lark",
        "feishu" to "com.ss.android.lark",
        "lark" to "com.ss.android.lark",
        "剪映" to "com.lemon.lv",
        "capcut" to "com.lemon.lv",
        
        // 阿里系
        "钉钉" to "com.alibaba.android.rimet",
        "dingding" to "com.alibaba.android.rimet",
        "淘宝" to "com.taobao.taobao",
        "taobao" to "com.taobao.taobao",
        "天猫" to "com.tmall.wireless",
        "tmall" to "com.tmall.wireless",
        "闲鱼" to "com.taobao.idlefish",
        "阿里巴巴" to "com.alibaba.wireless",
        
        // 陌陌系
        "陌陌" to "com.immomo.momo",
        "momo" to "com.immomo.momo",
        "探探" to "com.p1.mobile.putong",
        "tantan" to "com.p1.mobile.putong",
        "soul" to "cn.soulapp.android",
        
        // 知乎
        "知乎" to "com.zhihu.android",
        "zhihu" to "com.zhihu.android",
        
        // 贴吧
        "百度贴吧" to "com.baidu.tieba",
        "tieba" to "com.baidu.tieba",
        
        // 豆瓣
        "豆瓣" to "com.douban.frodo",
        "douban" to "com.douban.frodo",
        
        // 国际社交
        "Telegram" to "org.telegram.messenger",
        "telegram" to "org.telegram.messenger",
        "WhatsApp" to "com.whatsapp",
        "whatsapp" to "com.whatsapp",
        "Facebook" to "com.facebook.katana",
        "facebook" to "com.facebook.katana",
        "Messenger" to "com.facebook.orca",
        "Instagram" to "com.instagram.android",
        "instagram" to "com.instagram.android",
        "Twitter" to "com.twitter.android",
        "twitter" to "com.twitter.android",
        "X" to "com.twitter.android",
        "Discord" to "com.discord",
        "discord" to "com.discord",
        "Snapchat" to "com.snapchat.android",
        "snapchat" to "com.snapchat.android",
        "Pinterest" to "com.pinterest",
        "pinterest" to "com.pinterest",
        "LinkedIn" to "com.linkedin.android",
        "linkedin" to "com.linkedin.android",
        "Skype" to "com.skype.raider",
        "skype" to "com.skype.raider",
        
        // 其他
        "虎扑" to "com.hupu.shihuo",
        "hupu" to "com.hupu.shihuo",
        "小红书" to "com.xingin.xhs",
        "xhs" to "com.xingin.xhs",
        "最右" to "cn.luojilab",
        "uki" to "com.uki",
        "伊对" to "com.love.yidui",
        "有缘" to "com.youyuan.yyandroid",
        "百合" to "com.baihe",
        "世纪佳缘" to "com.jiayuan.app"
    )

    /**
     * 购物消费应用 (30个)
     */
    val SHOPPING = mapOf(
        // 主流电商
        "淘宝" to "com.taobao.taobao",
        "京东" to "com.jingdong.app.mall",
        "jd" to "com.jingdong.app.mall",
        "拼多多" to "com.xunmeng.pinduoduo",
        "pinduoduo" to "com.xunmeng.pinduoduo",
        "唯品会" to "com.achievo.vipshop",
        "vipshop" to "com.achievo.vipshop",
        "得物" to "com.dewuapp",
        "得间" to "com.dejian",
        "多抓鱼" to "com.duozhuayu",
        "红布林" to "com.koudai",
        
        // 外卖平台
        "美团" to "com.sankuai.meituan",
        "meituan" to "com.sankuai.meituan",
        "美团外卖" to "com.sankuai.meituan.takeoutnew",
        "大众点评" to "com.dianping.v1",
        "dianping" to "com.dianping.v1",
        "饿了么" to "me.ele",
        "eleme" to "me.ele",
        
        // 品牌官方
        "星巴克" to "com.starbucks.cn",
        "starbucks" to "com.starbucks.cn",
        "瑞幸咖啡" to "com.luckincoffee.app",
        "luckin" to "com.luckincoffee.app",
        "肯德基" to "com.yek.android.kfc.activitys",
        "kfc" to "com.yek.android.kfc.activitys",
        "麦当劳" to "com.mcdonalds.app",
        "mcdonalds" to "com.mcdonalds.app",
        "必胜客" to "com.pizzahut.pizzahutapp",
        "pizzahut" to "com.pizzahut.pizzahutapp",
        
        // 海淘
        "亚马逊" to "com.amazon.mShop.android.shopping",
        "amazon" to "com.amazon.mShop.android.shopping",
        "速卖通" to "com.alibaba.aliexpress",
        "aliexpress" to "com.alibaba.aliexpress",
        "shopee" to "com.shopee.id",
        "lazada" to "com.lazada.android",
        "eBay" to "com.ebay.mobile",
        "ebay" to "com.ebay.mobile",
        
        // 生活服务
        "贝壳找房" to "com.lianjia.beike",
        "beike" to "com.lianjia.beike",
        "安居客" to "com.anjuke.android.app",
        "anjuke" to "com.anjuke.android.app",
        "链家" to "com.lianjia.sh",
        "自如" to "com.ziroom.ziroom",
        "货拉拉" to "com.lalamove",
        "滴滴货运" to "com.didi.logistics",
        "闪送" to "com.isananda.shansong",
        "达达" to "com.imdada"
    )

    /**
     * 出行交通应用 (25个)
     */
    val TRANSPORTATION = mapOf(
        // 地图导航
        "高德地图" to "com.autonavi.minimap",
        "amap" to "com.autonavi.minimap",
        "百度地图" to "com.baidu.BaiduMap",
        "baidumap" to "com.baidu.BaiduMap",
        "腾讯地图" to "com.tencent.map",
        "tencentmap" to "com.tencent.map",
        "Google地图" to "com.google.android.apps.maps",
        "googlemaps" to "com.google.android.apps.maps",
        
        // 网约车
        "滴滴出行" to "com.sdu.didi.psnger",
        "didi" to "com.sdu.didi.psnger",
        "滴滴" to "com.sdu.didi.psnger",
        "曹操出行" to "com.caocaokeji",
        "首汽约车" to "com.01zhuanche",
        "T3出行" to "com.t3go",
        "如祺出行" to "com.rqrx",
        "嘀嗒出行" to "com.didapinche",
        
        // 共享单车
        "哈啰出行" to "com.jingyao.easybike",
        "hellobike" to "com.jingyao.easybike",
        "青桔单车" to "com.didi.bike.host",
        "美团单车" to "com.sankuai.mobike",
        "街兔" to "com.sankuai.mobike",
        
        // 机票火车
        "铁路12306" to "com.MobileTicket",
        "12306" to "com.MobileTicket",
        "携程" to "ctrip.android.view",
        "ctrip" to "ctrip.android.view",
        "去哪儿" to "com.Qunar",
        "qunar" to "com.Qunar",
        "飞猪" to "com.taobao.trip",
        "fliggy" to "com.taobao.trip",
        "马蜂窝" to "com.mafengwo.mall",
        "mafengwo" to "com.mafengwo.mall",
        "航旅纵横" to "com.smgbs",
        "航班管家" to "com.flightmanager",
        
        // 酒店
        "华住会" to "com.huazhu",
        "锦江酒店" to "com.jinjiang",
        "如家" to "com.homeinns",
        "亚朵" to "com.atto"
    )

    /**
     * 金融理财应用 (20个)
     */
    val FINANCE = mapOf(
        // 支付
        "支付宝" to "com.eg.android.AlipayGphone",
        "alipay" to "com.eg.android.AlipayGphone",
        "云闪付" to "com.unionpay",
        
        // 银行
        "招商银行" to "cmb.pb",
        "cmb" to "cmb.pb",
        "工商银行" to "com.icbc",
        "icbc" to "com.icbc",
        "建设银行" to "com.ccb.ccbnetpay",
        "ccb" to "com.ccb.ccbnetpay",
        "农业银行" to "com.abchina",
        "交通银行" to "com.bankcomm",
        "中国银行" to "com.chinamobile.boc",
        "邮储银行" to "com.psbc",
        "民生银行" to "com.cmbc",
        "兴业银行" to "com.cib",
        "平安银行" to "com.pingan",
        
        // 证券
        "同花顺" to "com.hexin.plat.android",
        "tonghuashun" to "com.hexin.plat.android",
        "东方财富" to "com.eastmoney.android.berlin",
        "eastmoney" to "com.eastmoney.android.berlin",
        "大智慧" to "com.dazhihui",
        "雪球" to "com.xueqiu.stock",
        "xueqiu" to "com.xueqiu.stock",
        "华泰证券" to "com.htsec.mobile",
        "国泰君安" to "com.gtja",
        
        // 基金理财
        "蚂蚁财富" to "com.alipay.phone.client",
        "京东金融" to "com.jd.jrapp",
        "陆金所" to "com.lufax",
        "微众银行" to "com.webank",
        "度小满" to "com.duxiaoman"
    )

    /**
     * 娱乐媒体应用 (35个)
     */
    val ENTERTAINMENT = mapOf(
        // 视频
        "哔哩哔哩" to "tv.danmaku.bili",
        "bilibili" to "tv.danmaku.bili",
        "b站" to "tv.danmaku.bili",
        "腾讯视频" to "com.tencent.qqlive",
        "tencentvideo" to "com.tencent.qqlive",
        "爱奇艺" to "com.qiyi.video",
        "iqiyi" to "com.qiyi.video",
        "优酷" to "com.youku.phone",
        "youku" to "com.youku.phone",
        "芒果TV" to "com.hunantv.imgo.activity",
        "mangotv" to "com.hunantv.imgo.activity",
        "搜狐视频" to "com.sohu.sohu",
        "乐视视频" to "com.letv",
        "PPTV" to "com.pplive.android",
        "咪咕视频" to "com.migu.video",
        "人人视频" to "com.rrmj.tv",
        "南瓜电影" to "com.nanmovip.android",
        
        // 短视频
        "快手" to "com.smile.gifmaker",
        "kuaishou" to "com.smile.gifmaker",
        "TikTok" to "com.zhiliaoapp.musically",
        "tiktok" to "com.zhiliaoapp.musically",
        "YouTube" to "com.google.android.youtube",
        "youtube" to "com.google.android.youtube",
        "Netflix" to "com.netflix.mediaclient",
        "netflix" to "com.netflix.mediaclient",
        "虎牙直播" to "com.duowan.lite",
        "斗鱼" to "air.tv.douyu",
        "Bilibili国际版" to "tv.danmaku.bili",
        
        // 音乐
        "网易云音乐" to "com.netease.cloudmusic",
        "cloudmusic" to "com.netease.cloudmusic",
        "QQ音乐" to "com.tencent.qqmusic",
        "qqmusic" to "com.tencent.qqmusic",
        "酷狗音乐" to "com.kugou.android",
        "kugou" to "com.kugou.android",
        "酷我音乐" to "com.kuwo.music",
        "kuwo" to "com.kuwo.music",
        "汽水音乐" to "com.luna.music",
        "qishui" to "com.luna.music",
        "喜马拉雅" to "com.ximalaya.ting.android",
        "ximalaya" to "com.ximalaya.ting.android",
        "荔枝FM" to "fm.lizhi.radio",
        "lizhi" to "fm.lizhi.radio",
        "蜻蜓FM" to "com.qtingfm",
        "全民K歌" to "com.tencent.karaoke",
        "唱吧" to "com.changba",
        "Spotify" to "com.spotify.music",
        "spotify" to "com.spotify.music",
        "Apple Music" to "com.apple.android.music",
        
        // 直播
        "抖音直播" to "com.ss.android.ugc.aweme.live",
        "快手直播" to "com.smile.gifmaker.live"
    )

    /**
     * 阅读学习应用 (25个)
     */
    val READING = mapOf(
        // 网络小说
        "番茄小说" to "com.dragon.read",
        "tomato" to "com.dragon.read",
        "七猫免费小说" to "com.kmxs.reader",
        "qimao" to "com.kmxs.reader",
        "起点读书" to "com.qidian.QDReader",
        "qidian" to "com.qidian.QDReader",
        "掌阅" to "com.zhangyue.read",
        "ireader" to "com.zhangyue.read",
        "微信读书" to "com.tencent.weread",
        "weread" to "com.tencent.weread",
        "QQ阅读" to "com.qq.reader",
        "书旗小说" to "com.shuqi.controller",
        "shuqi" to "com.shuqi.controller",
        "追书神器" to "com.duanshu",
        "疯读" to "com.cnew",
        "米读" to "com.midureader",
        
        // 有声书
        "懒人听书" to "com.tadu",
        "听书" to "com.tadu",
        "得到" to "com.luojilab.player",
        "樊登读书" to "com.dushu",
        "十点读书" to "com.reading",
        "混沌学园" to "com.hundun.university",
        
        // 新闻资讯
        "今日头条" to "com.ss.android.article.news",
        "腾讯新闻" to "com.tencent.news",
        "tencentnews" to "com.tencent.news",
        "网易新闻" to "com.netease.newsreader",
        "neteasenews" to "com.netease.newsreader",
        "澎湃新闻" to "com.thepaper",
        "凤凰新闻" to "com.ifeng.news",
        "搜狐新闻" to "com.sohu.news",
        "新京报" to "com.bjnews",
        "人民日报" to "com.peopleapp",
        "新华社" to "cn.xinhuanet"
    )

    /**
     * 效率工具应用 (25个)
     */
    val PRODUCTIVITY = mapOf(
        // 办公
        "WPS" to "cn.wps.moffice_eng",
        "wps" to "cn.wps.moffice_eng",
        "Microsoft Office" to "com.microsoft.office.officehubrow",
        "office" to "com.microsoft.office.officehubrow",
        "Word" to "com.microsoft.office.word",
        "Excel" to "com.microsoft.office.excel",
        "PowerPoint" to "com.microsoft.office.powerpoint",
        "Outlook" to "com.microsoft.office.outlook",
        
        // 笔记
        "印象笔记" to "com.yinxiang.main",
        "yinxiang" to "com.yinxiang.main",
        "有道云笔记" to "com.youdao.note",
        "youdao" to "com.youdao.note",
        "Notion" to "notion.id",
        "notion" to "notion.id",
        "OneNote" to "com.microsoft.office.onenote",
        "onenote" to "com.microsoft.office.onenote",
        "Evernote" to "com.evernote",
        "evernote" to "com.evernote",
        
        // 协作
        "飞书" to "com.ss.android.lark",
        "钉钉" to "com.alibaba.android.rimet",
        "企业微信" to "com.tencent.wework",
        "Slack" to "com.slack",
        "slack" to "com.slack",
        "Zoom" to "us.zoom.videomeetings",
        "zoom" to "us.zoom.videomeetings",
        "Teams" to "com.microsoft.teams",
        "teams" to "com.microsoft.teams",
        "腾讯会议" to "com.tencent.wemeet",
        
        // 任务管理
        "滴答清单" to "com.dida365.mandroid",
        "dida" to "com.dida365.mandroid",
        "Todoist" to "com.todoist",
        "todoist" to "com.todoist",
        "Any.do" to "com.anydo",
        "remember" to "com.rememberthemilk",
        "Trello" to "com.trello",
        
        // 云存储
        "百度网盘" to "com.baidu.netdisk",
        "baiduwangpan" to "com.baidu.netdisk",
        "阿里云盘" to "com.alicloud.datacenter",
        "腾讯微云" to "com.weiyun",
        "OneDrive" to "com.microsoft.skydrive",
        "Google Drive" to "com.google.android.apps.docs"
    )

    /**
     * 系统工具应用 (30个)
     */
    val SYSTEM = mapOf(
        // Android 系统
        "设置" to "com.android.settings",
        "settings" to "com.android.settings",
        "文件管理器" to "com.android.fileexplorer",
        "filemanager" to "com.android.fileexplorer",
        "浏览器" to "com.android.chrome",
        "chrome" to "com.android.chrome",
        "计算器" to "com.android.calculator2",
        "calculator" to "com.android.calculator2",
        "相机" to "com.android.camera2",
        "camera" to "com.android.camera2",
        "通讯录" to "com.android.contacts",
        "contacts" to "com.android.contacts",
        "日历" to "com.android.calendar",
        "calendar" to "com.android.calendar",
        "时钟" to "com.android.deskclock",
        "clock" to "com.android.deskclock",
        "天气" to "com.android.weather",
        "weather" to "com.android.weather",
        "图库" to "com.android.gallery3d",
        "gallery" to "com.android.gallery3d",
        "音乐" to "com.android.music",
        "music" to "com.android.music",
        "商店" to "com.android.vending",
        "playstore" to "com.android.vending",
        
        // Google 系统
        "Gmail" to "com.google.android.gm",
        "gmail" to "com.google.android.gm",
        "Google相册" to "com.google.android.apps.photos",
        "photos" to "com.google.android.apps.photos",
        "Google翻译" to "com.google.android.apps.translate",
        "translate" to "com.google.android.apps.translate",
        "Google Keep" to "com.google.android.keep",
        "keep" to "com.google.android.keep",
        "Google日历" to "com.google.android.calendar",
        "Google联系人" to "com.google.android.contacts",
        
        // 安全工具
        "360安全卫士" to "com.qihoo.security",
        "腾讯手机管家" to "com.tencent.qqpimsecure",
        "猎豹清理大师" to "com.cleanmaster.mguard",
        "手机管家" to "com.samsung.android.app.guard",
        "应用锁" to "com.Applock",
        "密码管理" to "com.lastpass",
        
        // 下载工具
        "IDM下载器" to "idm.internet.download.manager",
        "ADM" to "com.dv.adm",
        "迅雷" to "com.xunlei.downloadprovider",
        "xunlei" to "com.xunlei.downloadprovider"
    )

    /**
     * 游戏应用 (30个)
     */
    val GAMES = mapOf(
        // MOBA
        "王者荣耀" to "com.tencent.tmgp.sgame",
        "honorkings" to "com.tencent.tmgp.sgame",
        "和平精英" to "com.tencent.tmgp.pubgm",
        "pubg" to "com.tencent.tmgp.pubgm",
        "英雄联盟手游" to "com.riotgames.league.wildrift",
        "wildrift" to "com.riotgames.league.wildrift",
        
        // 米哈游
        "原神" to "com.miHoYo.ys",
        "genshin" to "com.miHoYo.ys",
        "崩坏星穹铁道" to "com.miHoYo.hkrpg",
        "starrail" to "com.miHoYo.hkrpg",
        "崩坏3" to "com.mihoyo.bh3.global",
        "honkai3rd" to "com.mihoyo.bh3.global",
        "崩坏2" to "com.mihoyo.bh2",
        "绝区零" to "com.miHoYo.zzz",
        
        // 网易游戏
        "阴阳师" to "com.netease.onmyoji",
        "onmyoji" to "com.netease.onmyoji",
        "荒野行动" to "com.netease.hxdy",
        "荒野" to "com.netease.hxdy",
        "明日之后" to "com.netease.mrzh",
        
        // 二次元
        "明日方舟" to "com.hypergryph.arknights",
        "arknights" to "com.hypergryph.arknights",
        "少女前线" to "com.sunborn.neona",
        "sunborn" to "com.sunborn.neona",
        "碧蓝航线" to "com.bilibili.azurlane",
        "azurlane" to "com.bilibili.azurlane",
        "FGO" to "com.aniplex.fategrandorder",
        "fgo" to "com.aniplex.fategrandorder",
        
        // 休闲
        "植物大战僵尸" to "com.popcap.pvz",
        "pvz" to "com.popcap.pvz",
        "地铁跑酷" to "com.kiloo.subwaysurf",
        "subwaysurf" to "com.kiloo.subwaysurf",
        "神庙逃亡" to "com.imangi.templerun",
        "temple" to "com.imangi.templerun",
        "纪念碑谷" to "com.ustwo.monumentvalley",
        "monument" to "com.ustwo.monumentvalley",
        "光遇" to "com.tgc.sky.android",
        "sky" to "com.tgc.sky.android",
        
        // 棋牌
        "欢乐斗地主" to "com.qqgame.hlddz",
        "斗地主" to "com.qqgame.hlddz",
        "欢乐麻将" to "com.qqgame.hlmj",
        "象棋" to "com.chess",
        "围棋" to "com.weiqi",
        "五子棋" to "com.gomoku"
    )

    /**
     * AI 工具应用 (15个)
     */
    val AI_TOOLS = mapOf(
        // 国内 AI
        "豆包" to "com.larus.nova",
        "doubao" to "com.larus.nova",
        "文心一言" to "com.baidu.wenyin",
        "ernie" to "com.baidu.wenyin",
        "讯飞星火" to "com.iflytek.spark",
        "spark" to "com.iflytek.spark",
        "通义千问" to "com.alibaba.ailabs",
        "tongyi" to "com.alibaba.ailabs",
        "智谱清言" to "com.chatglm",
        "chatglm" to "com.chatglm",
        "Kimi" to "com.moonshot",
        "kimi" to "com.moonshot",
        "天工AI" to "com.apollo",
        "tiangong" to "com.apollo",
        
        // 国际 AI
        "ChatGPT" to "com.openai.chatgpt",
        "chatgpt" to "com.openai.chatgpt",
        "Claude" to "com.anthropic.claude",
        "claude" to "com.anthropic.claude",
        "Gemini" to "com.google.android.apps.bard",
        "gemini" to "com.google.android.apps.bard",
        "Copilot" to "com.microsoft.copilot",
        "copilot" to "com.microsoft.copilot",
        "Perplexity" to "com.perplexity.ai",
        "perplexity" to "com.perplexity.ai"
    )

    /**
     * 经典映射 - 整合原有 AppPackageMapping 的别名和变体
     * 包含各种应用名称的变体、拼写变体、混合大小写等
     */
    val CLASSIC_MAPPING = mapOf(
        // ===== 原有 AppPackageMapping 的别名整合 =====
        
        // 微信生态变体
        "WeChat" to "com.tencent.mm",
        "weixin" to "com.tencent.mm",
        "WeiXin" to "com.tencent.mm",
        "wx" to "com.tencent.mm",
        
        // QQ 生态变体
        "TencentQQ" to "com.tencent.mobileqq",
        "mobileqq" to "com.tencent.mobileqq",
        
        // 微博变体
        "SinaWeibo" to "com.sina.weibo",
        "sinaweibo" to "com.sina.weibo",
        
        // 阿里系变体
        "Taobao" to "com.taobao.taobao",
        "taobao" to "com.taobao.taobao",
        "TMALL" to "com.tmall.wireless",
        "tmall" to "com.tmall.wireless",
        "AliWangWang" to "com.taobao.wangwang",
        "闲鱼" to "com.taobao.idlefish",
        "阿里巴巴" to "com.alibaba.wireless",
        
        // 字节系变体
        "Douyin" to "com.ss.android.ugc.aweme",
        "TengXunShiPin" to "com.tencent.qqlive",
        "Aiqiyi" to "com.qiyi.video",
        "YouKu" to "com.youku.phone",
        "MangoTV" to "com.hunantv.imgo.activity",
        
        // 百度系
        "Baidu" to "com.baidu.searchbox",
        "BaiduTieba" to "com.baidu.tieba",
        "BaiduMap" to "com.baidu.BaiduMap",
        "baidumap" to "com.baidu.BaiduMap",
        
        // 网易系
        "WangYiYunYinYue" to "com.netease.cloudmusic",
        "neteasemusic" to "com.netease.cloudmusic",
        "WangYiXinWen" to "com.netease.newsreader.activity",
        "neteasenews" to "com.netease.newsreader.activity",
        "WangYiYouXiang" to "com.netease.mail",
        "neteasemail" to "com.netease.mail",
        
        // Google 系统应用变体
        "GoogleGmail" to "com.google.android.gm",
        "GoogleMaps" to "com.google.android.apps.maps",
        "googlemaps" to "com.google.android.apps.maps",
        "GoogleChrome" to "com.android.chrome",
        "googlechrome" to "com.android.chrome",
        "GooglePlay" to "com.android.vending",
        "googleplay" to "com.android.vending",
        "GooglePhotos" to "com.google.android.apps.photos",
        "googlephotos" to "com.google.android.apps.photos",
        "GoogleDrive" to "com.google.android.apps.docs",
        "googledrive" to "com.google.android.apps.docs",
        "GoogleDocs" to "com.google.android.apps.docs.editors.docs",
        "googledocs" to "com.google.android.apps.docs.editors.docs",
        "GoogleCalendar" to "com.google.android.calendar",
        "googlecalendar" to "com.google.android.calendar",
        "GoogleTranslate" to "com.google.android.apps.translate",
        "googletranslate" to "com.google.android.apps.translate",
        "GoogleKeep" to "com.google.android.keep",
        "googlekeep" to "com.google.android.keep",
        
        // Android 系统变体
        "AndroidSettings" to "com.android.settings",
        "androidsettings" to "com.android.settings",
        "AndroidSystemSettings" to "com.android.settings",
        
        // 国际应用变体（与 ENTERTAINMENT/SHOPPING 重复的 YouTube/Netflix/Amazon 已删除）
        "Spotify" to "com.spotify.music",
        "spotify" to "com.spotify.music",
        "eBay" to "com.ebay.mobile",
        "ebay" to "com.ebay.mobile",
        "PayPal" to "com.paypal.android.p2pmobile",
        "paypal" to "com.paypal.android.p2pmobile",
        "Uber" to "com.ubercab",
        "uber" to "com.ubercab",
        "Zoom" to "us.zoom.videomeetings",
        "zoom" to "us.zoom.videomeetings",
        "Skype" to "com.skype.raider",
        "skype" to "com.skype.raider",
        "Snapchat" to "com.snapchat.android",
        "snapchat" to "com.snapchat.android",
        "LinkedIn" to "com.linkedin.android",
        "linkedin" to "com.linkedin.android",
        
        // 其他常用应用
        "Douyu" to "air.tv.douyu",
        "douyu" to "air.tv.douyu",
        "HuYa" to "com.duowan.lite",
        "huyalive" to "com.duowan.lite",
        "MeiTuXiuXiu" to "com.mt.mtxx.mtxx",
        "meitu" to "com.mt.mtxx.mtxx",
        "MeiZhouMiFeng" to "com.ss.android.ugc.trill",
        "mifeng" to "com.ss.android.ugc.trill",
        "SuZhouTong" to "com.suning.mobile.ebuy",
        "suning" to "com.suning.mobile.ebuy",
        "SuNingYiGou" to "com.suning.mobile.ebuy",
        "TongCheng" to "com.sankuai.meituan",
        "tongcheng" to "com.sankuai.meituan",
        "XieCheng" to "ctrip.android.view",
        "xiecheng" to "ctrip.android.view",
        "Qunar" to "com.Qunar",
        "qunar" to "com.Qunar",
        "TongHuaShun" to "com.hexin.plat.android",
        "tonghuashun" to "com.hexin.plat.android",
        "DongFangCaiFu" to "com.eastmoney.android.berlin",
        "dongfangcaifu" to "com.eastmoney.android.berlin",
        "XueQiu" to "com.xueqiu.stock",
        "xueqiu" to "com.xueqiu.stock",
        "JianSheYinHang" to "com.ccb.ccbnetpay",
        "jianhang" to "com.ccb.ccbnetpay",
        "GongShangYinHang" to "com.icbc",
        "gongshang" to "com.icbc",
        "ZhaoShangYinHang" to "cmb.pb",
        "zhaoshang" to "cmb.pb",
        "NongYeYinHang" to "com.abchina",
        "nongye" to "com.abchina",
        
        // 医疗健康
        "Guahao" to "com.guahao001.proj.yygh_app",
        "guahao" to "com.guahao001.proj.yygh_app",
        
        // 视频会议
        "TengXunHuiYi" to "com.tencent.wemeet",
        "tencentmeeting" to "com.tencent.wemeet",
        
        // 常用工具
        "WPSOffice" to "cn.wps.moffice_eng",
        "wps" to "cn.wps.moffice_eng",
        "MicrosoftOffice" to "com.microsoft.office.officehubrow",
        "msoffice" to "com.microsoft.office.officehubrow",
        
        // 更多国际应用
        "Duolingo" to "com.duolingo",
        "duolingo" to "com.duolingo",
        "Temu" to "com.einnovation.temu",
        "temu" to "com.einnovation.temu",
        "Booking" to "com.booking",
        "booking" to "com.booking",
        "Expedia" to "com.expedia.bookings",
        "expedia" to "com.expedia.bookings",
        "Joplin" to "net.cozic.joplin",
        "joplin" to "net.cozic.joplin",
        "OsmAnd" to "net.osmand",
        "osmand" to "net.osmand",
        "Reddit" to "com.reddit.frontpage",
        "reddit" to "com.reddit.frontpage",
        "VLC" to "org.videolan.vlc",
        "vlc" to "org.videolan.vlc",
        "Whatsapp" to "com.whatsapp",
        "whatsapp" to "com.whatsapp"
    )

    /**
     * 获取所有应用映射
     */
    fun getAllMappings(): Map<String, String> {
        return (SOCIAL_COMMUNICATION + SHOPPING + TRANSPORTATION + 
                FINANCE + ENTERTAINMENT + READING + 
                PRODUCTIVITY + SYSTEM + GAMES + AI_TOOLS + CLASSIC_MAPPING)
            .mapKeys { it.key.lowercase() }
    }

    /**
     * 根据类别获取应用映射
     */
    fun getMappingsByCategory(category: String): Map<String, String> {
        return when (category.lowercase()) {
            "social", "social_communication" -> SOCIAL_COMMUNICATION
            "shopping" -> SHOPPING
            "transportation", "transport" -> TRANSPORTATION
            "finance", "bank" -> FINANCE
            "entertainment", "media" -> ENTERTAINMENT
            "reading", "book" -> READING
            "productivity", "work" -> PRODUCTIVITY
            "system" -> SYSTEM
            "game", "games" -> GAMES
            "ai", "ai_tools" -> AI_TOOLS
            "classic", "legacy" -> CLASSIC_MAPPING
            else -> emptyMap()
        }
    }
}
