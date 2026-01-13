package com.ai.phoneagent.core.tools

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.LinkedHashMap

/**
 * 应用包名管理器
 * 负责缓存和快速查询已安装应用列表
 */
object AppPackageManager {
    
    // 应用缓存（包名 -> 应用名）
    private val appCache = mutableMapOf<String, String>()
    
    // 反向映射（应用名 -> 包名）
    private val appNameToPackage = mutableMapOf<String, String>()
    private const val RESOLVE_CACHE_TTL_MS = 300_000L // 5分钟
    private const val RESOLVE_CACHE_MAX_ENTRIES = 256
    private val resolveCache = object : LinkedHashMap<String, Pair<String, Long>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, Long>>?): Boolean {
            return size > RESOLVE_CACHE_MAX_ENTRIES
        }
    }

    private val predefinedAppPackages: Map<String, String> =
            listOf(
                            // ===== 中文社交与通讯 =====
                            "微信" to "com.tencent.mm",
                            "wechat" to "com.tencent.mm",
                            "qq" to "com.tencent.mobileqq",
                            "微博" to "com.sina.weibo",
                            "新浪微博" to "com.sina.weibo",
                            "weibo" to "com.sina.weibo",
                            "钉钉" to "com.alibaba.android.rimet",
                            "dingding" to "com.alibaba.android.rimet",
                            "企业微信" to "com.tencent.wework",
                            "wework" to "com.tencent.wework",
                            "陌陌" to "com.immomo.momo",
                            "momo" to "com.immomo.momo",
                            "探探" to "com.p1.mobile.putong",
                            "tantan" to "com.p1.mobile.putong",
                            "soul" to "cn.soulapp.android",
                            
                            // ===== 中文电商 =====
                            "淘宝" to "com.taobao.taobao",
                            "淘宝闪购" to "com.taobao.taobao",
                            "taobao" to "com.taobao.taobao",
                            "天猫" to "com.tmall.wireless",
                            "tmall" to "com.tmall.wireless",
                            "京东" to "com.jingdong.app.mall",
                            "京东秒送" to "com.jingdong.app.mall",
                            "jd" to "com.jingdong.app.mall",
                            "jingdong" to "com.jingdong.app.mall",
                            "拼多多" to "com.xunmeng.pinduoduo",
                            "pdd" to "com.xunmeng.pinduoduo",
                            "pinduoduo" to "com.xunmeng.pinduoduo",
                            "temu" to "com.einnovation.temu",
                            "苏宁易购" to "com.suning.mobile.ebuy",
                            "suning" to "com.suning.mobile.ebuy",
                            "唯品会" to "com.achievo.vipshop",
                            "vip" to "com.achievo.vipshop",
                            
                            // ===== 中文生活与社区 =====
                            "小红书" to "com.xingin.xhs",
                            "xhs" to "com.xingin.xhs",
                            "redbook" to "com.xingin.xhs",
                            "豆瓣" to "com.douban.frodo",
                            "douban" to "com.douban.frodo",
                            "知乎" to "com.zhihu.android",
                            "zhihu" to "com.zhihu.android",
                            "贴吧" to "com.baidu.tieba",
                            "tieba" to "com.baidu.tieba",
                            "百度贴吧" to "com.baidu.tieba",
                            "虎扑" to "com.hupu.shihuo",
                            "hupu" to "com.hupu.shihuo",
                            "下厨房" to "com.xiachufang",
                            "厨房" to "com.xiachufang",
                            "大麦" to "cn.damai",
                            "damai" to "cn.damai",
                            
                            // ===== 地图与导航 =====
                            "高德地图" to "com.autonavi.minimap",
                            "amap" to "com.autonavi.minimap",
                            "百度地图" to "com.baidu.BaiduMap",
                            "baidumap" to "com.baidu.BaiduMap",
                            "腾讯地图" to "com.tencent.map",
                            "tencent maps" to "com.tencent.map",
                            "搜狗地图" to "com.sogou.map.android.maps",
                            "sogou maps" to "com.sogou.map.android.maps",
                            
                            // ===== 餐饮与外卖 =====
                            "美团" to "com.sankuai.meituan",
                            "meituan" to "com.sankuai.meituan",
                            "美团外卖" to "com.sankuai.meituan.takeoutnew",
                            "meituan waimai" to "com.sankuai.meituan.takeoutnew",
                            "大众点评" to "com.dianping.v1",
                            "dianping" to "com.dianping.v1",
                            "饿了么" to "me.ele",
                            "eleme" to "me.ele",
                            "肯德基" to "com.yek.android.kfc.activitys",
                            "kfc" to "com.yek.android.kfc.activitys",
                            "mcdonald" to "com.mcdonalds.app",
                            "mcdonalds" to "com.mcdonalds.app",
                            "必胜客" to "com.pizzahut.pizzahutapp",
                            "pizza hut" to "com.pizzahut.pizzahutapp",
                            "星巴克" to "com.starbucks.cn",
                            "starbucks" to "com.starbucks.cn",
                            "瑞幸咖啡" to "com.luckincoffee.app",
                            "luckin" to "com.luckincoffee.app",
                            
                            // ===== 旅行与出行 =====
                            "携程" to "ctrip.android.view",
                            "ctrip" to "ctrip.android.view",
                            "铁路12306" to "com.MobileTicket",
                            "12306" to "com.MobileTicket",
                            "去哪儿" to "com.Qunar",
                            "去哪儿旅行" to "com.Qunar",
                            "qunar" to "com.Qunar",
                            "滴滴出行" to "com.sdu.didi.psnger",
                            "didi" to "com.sdu.didi.psnger",
                            "滴滴" to "com.sdu.didi.psnger",
                            "飞猪" to "com.taobao.trip",
                            "fliggy" to "com.taobao.trip",
                            "马蜂窝" to "com.mafengwo.mall",
                            "mafengwo" to "com.mafengwo.mall",
                            "airbnb" to "com.airbnb.android",
                            "booking.com" to "com.booking",
                            "booking" to "com.booking",
                            "expedia" to "com.expedia.bookings",
                            "agoda" to "com.agoda.mobile.consumer",
                            "哈啰出行" to "com.jingyao.easybike",
                            "hellobike" to "com.jingyao.easybike",
                            "青桔单车" to "com.didi.bike.host",
                            "qingju" to "com.didi.bike.host",
                            
                            // ===== 视频与娱乐 =====
                            "bilibili" to "tv.danmaku.bili",
                            "b站" to "tv.danmaku.bili",
                            "哔哩哔哩" to "tv.danmaku.bili",
                            "抖音" to "com.ss.android.ugc.aweme",
                            "douyin" to "com.ss.android.ugc.aweme",
                            "快手" to "com.smile.gifmaker",
                            "kuaishou" to "com.smile.gifmaker",
                            "腾讯视频" to "com.tencent.qqlive",
                            "tencent video" to "com.tencent.qqlive",
                            "爱奇艺" to "com.qiyi.video",
                            "iqiyi" to "com.qiyi.video",
                            "优酷视频" to "com.youku.phone",
                            "youku" to "com.youku.phone",
                            "芒果tv" to "com.hunantv.imgo.activity",
                            "mango tv" to "com.hunantv.imgo.activity",
                            "红果短剧" to "com.phoenix.read",
                            "tiktok" to "com.zhiliaoapp.musically",
                            "youtube" to "com.google.android.youtube",
                            "netflix" to "com.netflix.mediaclient",
                            "西瓜视频" to "com.ss.android.article.video",
                            "xigua" to "com.ss.android.article.video",
                            "好看视频" to "com.baidu.haokan",
                            "haokan" to "com.baidu.haokan",
                            
                            // ===== 音乐与音频 =====
                            "网易云音乐" to "com.netease.cloudmusic",
                            "netease music" to "com.netease.cloudmusic",
                            "cloudmusic" to "com.netease.cloudmusic",
                            "qq音乐" to "com.tencent.qqmusic",
                            "qq music" to "com.tencent.qqmusic",
                            "汽水音乐" to "com.luna.music",
                            "qishui music" to "com.luna.music",
                            "喜马拉雅" to "com.ximalaya.ting.android",
                            "ximalaya" to "com.ximalaya.ting.android",
                            "spotify" to "com.spotify.music",
                            "apple music" to "com.apple.android.music",
                            "全民k歌" to "com.tencent.karaoke",
                            "karaoke" to "com.tencent.karaoke",
                            "唱吧" to "com.alibaba.ailabs.genie.webapps",
                            "changba" to "com.alibaba.ailabs.genie.webapps",
                            "荔枝" to "fm.lizhi.radio",
                            "lizhi" to "fm.lizhi.radio",
                            
                            // ===== 阅读 =====
                            "番茄小说" to "com.dragon.read",
                            "番茄免费小说" to "com.dragon.read",
                            "tomato novel" to "com.dragon.read",
                            "七猫免费小说" to "com.kmxs.reader",
                            "qimao" to "com.kmxs.reader",
                            "起点读书" to "com.qidian.QDReader",
                            "qidian" to "com.qidian.QDReader",
                            "掌阅" to "com.zhangyue.read",
                            "ireader" to "com.zhangyue.read",
                            "微信读书" to "com.tencent.weread",
                            "weread" to "com.tencent.weread",
                            "kindle" to "com.amazon.kindle",
                            "书旗小说" to "com.shuqi.controller",
                            "shuqi" to "com.shuqi.controller",
                            
                            // ===== 生产力工具 =====
                            "飞书" to "com.ss.android.lark",
                            "feishu" to "com.ss.android.lark",
                            "lark" to "com.ss.android.lark",
                            "qq邮箱" to "com.tencent.androidqqmail",
                            "qq mail" to "com.tencent.androidqqmail",
                            "网易邮箱" to "com.netease.mail",
                            "netease mail" to "com.netease.mail",
                            "万得office" to "cn.wps.moffice_eng",
                            "wps" to "cn.wps.moffice_eng",
                            "office" to "com.microsoft.office.officehubrow",
                            "microsoft office" to "com.microsoft.office.officehubrow",
                            "word" to "com.microsoft.office.word",
                            "excel" to "com.microsoft.office.excel",
                            "powerpoint" to "com.microsoft.office.powerpoint",
                            "支付宝" to "com.eg.android.AlipayGphone",
                            "alipay" to "com.eg.android.AlipayGphone",
                            "银行" to "com.icbc",
                            "icbc" to "com.icbc",
                            
                            // ===== AI与工具 =====
                            "豆包" to "com.larus.nova",
                            "doubao" to "com.larus.nova",
                            "chatgpt" to "com.openai.chatgpt",
                            "文心一言" to "com.iflytek.spark",
                            "spark" to "com.iflytek.spark",
                            "科大讯飞" to "com.iflytek.inputmethod",
                            "iflytek" to "com.iflytek.inputmethod",
                            
                            // ===== 健康与运动 =====
                            "keep" to "com.gotokeep.keep",
                            "美柚" to "com.lingan.seeyou",
                            "meiyou" to "com.lingan.seeyou",
                            "薪豆" to "com.jinxin.xindong",
                            "mint" to "com.jinxin.xindong",
                            "步多多" to "com.pdd.health",
                            "buduo" to "com.pdd.health",
                            "春雨计步" to "com.jiuxian.weather.pedometer",
                            "pedometer" to "com.jiuxian.weather.pedometer",
                            "丁香医生" to "com.dingxiangyisheng.dxy",
                            "dingxiang" to "com.dingxiangyisheng.dxy",
                            
                            // ===== 新闻与资讯 =====
                            "腾讯新闻" to "com.tencent.news",
                            "tencent news" to "com.tencent.news",
                            "今日头条" to "com.ss.android.article.news",
                            "toutiao" to "com.ss.android.article.news",
                            "网易新闻" to "com.netease.newsreader.activity",
                            "netease news" to "com.netease.newsreader.activity",
                            "经济日报" to "com.smartnews.jrj",
                            "百度" to "com.baidu.searchbox",
                            "baidu" to "com.baidu.searchbox",
                            
                            // ===== 房产 =====
                            "贝壳找房" to "com.lianjia.beike",
                            "beike" to "com.lianjia.beike",
                            "安居客" to "com.anjuke.android.app",
                            "anjuke" to "com.anjuke.android.app",
                            "链家" to "com.lianjia.sh",
                            "lianjia" to "com.lianjia.sh",
                            "中原地产" to "com.zhongyuan.centaline",
                            "centaline" to "com.zhongyuan.centaline",
                            
                            // ===== 金融 =====
                            "同花顺" to "com.hexin.plat.android",
                            "tonghuashun" to "com.hexin.plat.android",
                            "东方财富" to "com.eastmoney.android.berlin",
                            "eastmoney" to "com.eastmoney.android.berlin",
                            "建行" to "com.ccb.ccbnetpay",
                            "ccb" to "com.ccb.ccbnetpay",
                            "工商银行" to "com.icbc",
                            "招商银行" to "cmb.pb",
                            "cmb" to "cmb.pb",
                            
                            // ===== 游戏 =====
                            "星穹铁道" to "com.miHoYo.hkrpg",
                            "崩坏：星穹铁道" to "com.miHoYo.hkrpg",
                            "honkai star rail" to "com.miHoYo.hkrpg",
                            "崩坏3" to "com.mihoyo.bh3.global",
                            "honkai impact" to "com.mihoyo.bh3.global",
                            "原神" to "com.miHoYo.ys",
                            "genshin impact" to "com.miHoYo.ys",
                            "恋与深空" to "com.papegames.lysk.cn",
                            "love and deepspace" to "com.papegames.lysk.cn",
                            "明日方舟" to "com.hypergryph.arknights",
                            "arknights" to "com.hypergryph.arknights",
                            "阴阳师" to "com.netease.onmyoji",
                            "onmyoji" to "com.netease.onmyoji",
                            "王者荣耀" to "com.tencent.tmgp.sgame",
                            "honor of kings" to "com.tencent.tmgp.sgame",
                            "和平精英" to "com.tencent.tmgp.pubgm",
                            "pubg mobile" to "com.tencent.tmgp.pubgm",
                            "minecraft" to "com.mojang.minecraftpe",
                            "我的世界" to "com.mojang.minecraftpe",
                            
                            // ===== Android系统设置 (多种变体) =====
                            "androidsystemsettings" to "com.android.settings",
                            "android system settings" to "com.android.settings",
                            "android  system settings" to "com.android.settings",
                            "android-system-settings" to "com.android.settings",
                            "settings" to "com.android.settings",
                            
                            // ===== Android系统工具 =====
                            "audiorecorder" to "com.android.soundrecorder",
                            "chrome" to "com.android.chrome",
                            "google chrome" to "com.android.chrome",
                            "clock" to "com.android.deskclock",
                            "contacts" to "com.android.contacts",
                            "files" to "com.android.fileexplorer",
                            "file manager" to "com.android.fileexplorer",
                            
                            // ===== Google生态系统 =====
                            "gmail" to "com.google.android.gm",
                            "googlemail" to "com.google.android.gm",
                            "google mail" to "com.google.android.gm",
                            "googlefiles" to "com.google.android.apps.nbu.files",
                            "filesbygoogle" to "com.google.android.apps.nbu.files",
                            "files by google" to "com.google.android.apps.nbu.files",
                            "googlecalendar" to "com.google.android.calendar",
                            "google-calendar" to "com.google.android.calendar",
                            "google calendar" to "com.google.android.calendar",
                            "googlechat" to "com.google.android.apps.dynamite",
                            "google chat" to "com.google.android.apps.dynamite",
                            "google-chat" to "com.google.android.apps.dynamite",
                            "googleclock" to "com.google.android.deskclock",
                            "google-clock" to "com.google.android.deskclock",
                            "google clock" to "com.google.android.deskclock",
                            "googlecontacts" to "com.google.android.contacts",
                            "google-contacts" to "com.google.android.contacts",
                            "google contacts" to "com.google.android.contacts",
                            "googledocs" to "com.google.android.apps.docs.editors.docs",
                            "google docs" to "com.google.android.apps.docs.editors.docs",
                            "google drive" to "com.google.android.apps.docs",
                            "google-drive" to "com.google.android.apps.docs",
                            "googledrive" to "com.google.android.apps.docs",
                            "googlefit" to "com.google.android.apps.fitness",
                            "google fit" to "com.google.android.apps.fitness",
                            "googlekeep" to "com.google.android.keep",
                            "google keep" to "com.google.android.keep",
                            "googlemaps" to "com.google.android.apps.maps",
                            "google maps" to "com.google.android.apps.maps",
                            "google play books" to "com.google.android.apps.books",
                            "google-play-books" to "com.google.android.apps.books",
                            "googleplaybooks" to "com.google.android.apps.books",
                            "googleplaystore" to "com.android.vending",
                            "google play store" to "com.android.vending",
                            "google-play-store" to "com.android.vending",
                            "play store" to "com.android.vending",
                            "googleslides" to "com.google.android.apps.docs.editors.slides",
                            "google slides" to "com.google.android.apps.docs.editors.slides",
                            "google-slides" to "com.google.android.apps.docs.editors.slides",
                            "googletasks" to "com.google.android.apps.tasks",
                            "google-tasks" to "com.google.android.apps.tasks",
                            "google tasks" to "com.google.android.apps.tasks",
                            "google translate" to "com.google.android.apps.translate",
                            "translate" to "com.google.android.apps.translate",
                            "google photos" to "com.google.android.apps.photos",
                            "photos" to "com.google.android.apps.photos",
                            
                            // ===== 国际第三方应用 =====
                            "bluecoins" to "com.rammigsoftware.bluecoins",
                            "broccoli" to "com.flauschcode.broccoli",
                            "duolingo" to "com.duolingo",
                            "joplin" to "net.cozic.joplin",
                            "osmand" to "net.osmand",
                            "pimusicplayer" to "com.Project100Pi.themusicplayer",
                            "quora" to "com.quora.android",
                            "reddit" to "com.reddit.frontpage",
                            "retromusic" to "code.name.monkey.retromusic",
                            "simplecalendarpro" to "com.scientificcalculatorplus.simplecalculator.basiccalculator.mathcalc",
                            "simplesmsmessenger" to "com.simplemobiletools.smsmessenger",
                            "telegram" to "org.telegram.messenger",
                            "twitter" to "com.twitter.android",
                            "x" to "com.twitter.android",
                            "vlc" to "org.videolan.vlc",
                            "whatsapp" to "com.whatsapp",
                            "facebook" to "com.facebook.katana",
                            "instagram" to "com.instagram.android",
                            "discord" to "com.discord",
                            "zoom" to "us.zoom.videomeetings",
                            "skype" to "com.skype.raider",
                            "snapchat" to "com.snapchat.android",
                            "pinterest" to "com.pinterest",
                            "linkedin" to "com.linkedin.android",
                            "uber" to "com.ubercab",
                            "lyft" to "com.lyft",
                            "paypal" to "com.paypal.android.p2pmobile",
                            "amazon" to "com.amazon.mshop.android.shopping",
                            "ebay" to "com.ebay.mobile",
                    )
                    .associate { (name, pkg) -> name.lowercase() to pkg }
    
    private var lastUpdateTime = 0L
    private const val CACHE_VALIDITY_MS = 300000 // 5分钟缓存时间

    private fun preloadPredefinedMappings() {
        predefinedAppPackages.forEach { (name, pkg) ->
            appNameToPackage[name] = pkg
            appNameToPackage[pkg.lowercase()] = pkg
        }
    }

    private fun cacheResolution(key: String, packageName: String) {
        synchronized(resolveCache) {
            resolveCache[key] = packageName to System.currentTimeMillis()
        }
    }
    
    /**
     * 初始化应用列表缓存
     */
    fun initializeCache(context: Context) {
        val currentTime = System.currentTimeMillis()
        
        // 如果缓存有效，直接返回
        if (currentTime - lastUpdateTime < CACHE_VALIDITY_MS && appCache.isNotEmpty()) {
            return
        }
        
        appCache.clear()
        appNameToPackage.clear()
        synchronized(resolveCache) { resolveCache.clear() }
        preloadPredefinedMappings()
        
        try {
            val packageManager = context.packageManager
            val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            for (app in installedPackages) {
                // 只缓存用户安装的应用（非系统应用）
                if (app.flags and ApplicationInfo.FLAG_SYSTEM == 0 || isImportantSystemApp(app.packageName)) {
                    val appName = packageManager.getApplicationLabel(app).toString()
                    appCache[app.packageName] = appName
                    appNameToPackage[appName.lowercase()] = app.packageName
                    
                    // 也缓存包名本身（以防用户直接用包名）
                    appNameToPackage[app.packageName.lowercase()] = app.packageName
                }
            }
            
            lastUpdateTime = currentTime
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 是否为重要系统应用（保留）
     */
    private fun isImportantSystemApp(packageName: String): Boolean {
        val importantApps = setOf(
            "com.android.settings",           // 设置
            "com.android.chrome",              // Chrome
            "com.google.android.gms",          // Google服务
            "com.android.dialer",              // 拨号
            "com.android.phone",               // 电话
            "com.android.contacts",            // 联系人
            "com.android.messaging"            // 短信
        )
        return importantApps.contains(packageName)
    }
    
    /**
     * 根据应用名或包名解析包名
     * @param query 应用名或包名
     * @return 包名，未找到返回null
     */
    fun resolvePackageName(query: String?): String? {
        if (query.isNullOrBlank()) return null
        
        val trimmedQuery = query.trim()
        val lowerQuery = trimmedQuery.lowercase()
        
        // 命中LRU缓存（带TTL）
        synchronized(resolveCache) {
            resolveCache[lowerQuery]?.let { (pkg, ts) ->
                if (System.currentTimeMillis() - ts < RESOLVE_CACHE_TTL_MS) {
                    return pkg
                } else {
                    resolveCache.remove(lowerQuery)
                }
            }
        }
        
        fun record(pkg: String): String {
            cacheResolution(lowerQuery, pkg)
            return pkg
        }
        
        // 首先尝试直接匹配（包名或精确应用名）
        appNameToPackage[lowerQuery]?.let { return record(it) }
        appNameToPackage[trimmedQuery]?.let { return record(it) }
        
        // 模糊匹配：查找包含该关键字的应用
        val keyword = lowerQuery
        appNameToPackage.entries.firstOrNull { (name, _) ->
            name.contains(keyword) && !name.startsWith(".")
        }?.value?.let { return record(it) }
        
        // 反向查找：查找应用名包含关键字
        appCache.entries.firstOrNull { (_, appName) ->
            appName.lowercase().contains(keyword)
        }?.key?.let { return record(it) }
        
        return null
    }
    
    /**
     * 获取应用名
     */
    fun getAppName(packageName: String): String {
        return appCache[packageName] ?: packageName
    }
    
    /**
     * 获取所有已安装应用列表（用于显示）
     */
    fun getAllInstalledApps(): List<Pair<String, String>> {
        return appCache.map { (packageName, appName) ->
            packageName to appName
        }
    }
    
    /**
     * 清除缓存（手动刷新）
     */
    fun clearCache() {
        appCache.clear()
        appNameToPackage.clear()
        synchronized(resolveCache) { resolveCache.clear() }
        lastUpdateTime = 0L
    }
}
