/*
METADATA
{
    "name": "jmcomic_downloader",
    "description": "提供JMComic漫画下载功能，支持搜索、获取信息和下载，包括对新漫画的图片反爬解码。",
    "tools": [
        {
            "name": "main",
            "description": "运行一个内置的测试函数，以验证JMComic工具的基本功能（搜索和获取信息）是否正常工作。",
            "parameters": []
        },
        {
            "name": "search_comics",
            "description": "搜索JMComic漫画",
            "parameters": [
                {
                    "name": "query",
                    "description": "搜索关键词",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "page",
                    "description": "页码 (默认: 1)",
                    "type": "number",
                    "required": false
                },
                {
                    "name": "order_by",
                    "description": "排序方式 (latest, view, picture, like, 默认: view)",
                    "type": "string",
                    "required": false
                },
                {
                    "name": "time",
                    "description": "时间范围 (today, week, month, all, 默认: all)",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "get_album_info",
            "description": "获取漫画（本子）的详细信息",
            "parameters": [
                {
                    "name": "album_id",
                    "description": "漫画ID",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "download_album",
            "description": "下载指定ID的单本漫画，包含图片解码功能。",
            "parameters": [
                {
                    "name": "album_id",
                    "description": "要下载的漫画ID",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "download_dir",
                    "description": "下载目录 (可选, 默认: /sdcard/Download/OperitScripts)",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "batch_download_albums",
            "description": "批量下载多本漫画，包含图片解码功能。",
            "parameters": [
                {
                    "name": "album_ids",
                    "description": "要下载的漫画ID列表，用逗号分隔",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "download_dir",
                    "description": "下载目录 (可选, 默认: /sdcard/Download/OperitScripts)",
                    "type": "string",
                    "required": false
                }
            ]
        }
    ],
    "enabledByDefault": false
}
*/
// endregion
const jmcomic = (function () {
    // region Polyfill & Utils
    // Buffer a subset of Buffer functionality for base64 encoding/decoding
    const Buffer = {
        from: (str, encoding = 'utf8') => {
            if (encoding === 'base64') {
                return atob(str);
            }
            else if (encoding === 'hex') {
                let s = '';
                for (let i = 0; i < str.length; i += 2) {
                    s += String.fromCharCode(parseInt(str.substr(i, 2), 16));
                }
                return s;
            }
            return str;
        },
        toString: (buf, encoding = 'utf8') => {
            if (encoding === 'base64') {
                return btoa(buf);
            }
            else if (encoding === 'hex') {
                let s = '';
                for (let i = 0; i < buf.length; i++) {
                    s += ('0' + buf.charCodeAt(i).toString(16)).slice(-2);
                }
                return s;
            }
            return buf;
        }
    };
    function joinPath(...segments) {
        return segments.join('/').replace(/\/+/g, '/');
    }
    function dirname(filePath) {
        const lastSlashPos = filePath.lastIndexOf('/');
        if (lastSlashPos === -1) {
            return ".";
        }
        if (lastSlashPos === 0) {
            return "/";
        }
        return filePath.substring(0, lastSlashPos);
    }
    async function ensureDirExists(dirPath) {
        if (!dirPath || dirPath === '/' || dirPath === '.') {
            return;
        }
        const dirExists = await Tools.Files.exists(dirPath);
        if (dirExists.exists) {
            return;
        }
        const parentDir = dirname(dirPath);
        await ensureDirExists(parentDir);
        const dirStillNotExists = await Tools.Files.exists(dirPath);
        if (!dirStillNotExists.exists) {
            await Tools.Files.mkdir(dirPath);
        }
    }
    function basename(filePath) {
        return filePath.substring(filePath.lastIndexOf('/') + 1);
    }
    async function runTasksWithConcurrency(tasks, limit) {
        const results = new Array(tasks.length);
        let currentIndex = 0;
        async function runner() {
            while (currentIndex < tasks.length) {
                const taskIndex = currentIndex++;
                if (taskIndex < tasks.length) {
                    try {
                        results[taskIndex] = await tasks[taskIndex]();
                    }
                    catch (e) {
                        console.error(`并发任务 ${taskIndex} 执行失败: ${e.message}`);
                        results[taskIndex] = e;
                    }
                }
            }
        }
        const runners = [];
        const numRunners = Math.min(limit, tasks.length);
        for (let i = 0; i < numRunners; i++) {
            runners.push(runner());
        }
        await Promise.all(runners);
        return results.filter(r => !(r instanceof Error));
    }
    // endregion
    // region Constants and Classes from jmcomic
    const __version__ = '2.6.4-ts-adapted';
    function shuffleDomains(domains) {
        const shuffled = [...domains];
        for (let i = shuffled.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
        }
        return shuffled;
    }
    const JmMagicConstants = {
        APP_TOKEN_SECRET: '18comicAPP',
        APP_TOKEN_SECRET_2: '18comicAPPContent',
        APP_DATA_SECRET: '185Hcomic3PAPP7R',
        APP_VERSION: '1.8.0',
        SCRAMBLE_220980: 220980,
        SCRAMBLE_268850: 268850,
        SCRAMBLE_421926: 421926
    };
    const JmModuleConfig = {
        PROT: 'https://',
        DOMAIN_API_LIST: shuffleDomains([
            'www.cdnmhwscc.vip',
            'www.cdnplaystation6.club',
            'www.cdnplaystation6.org',
            'www.cdnuc.vip',
            'www.cdn-mspjmapiproxy.xyz'
        ]),
        DOMAIN_IMAGE_LIST: shuffleDomains([
            'cdn-msp.jmapiproxy1.cc',
            'cdn-msp.jmapiproxy2.cc',
            'cdn-msp2.jmapiproxy2.cc',
            'cdn-msp3.jmapiproxy2.cc'
        ]),
        APP_HEADERS_TEMPLATE: {
            'Accept-Language': 'zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7',
            'X-Requested-With': 'com.jiaohua_browser',
            'user-agent': 'Mozilla/5.0 (Linux; Android 9; V1938CT Build/PQ3A.190705.11211812; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36',
        },
        APP_HEADERS_IMAGE: {
            'Accept': 'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8',
            'Accept-Language': 'zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7',
            'X-Requested-With': 'com.jiaohua_browser',
        }
    };
    class JmImageTool {
        static getNum(scrambleId, photoId, imageName) {
            const scrambleIdNum = parseInt(scrambleId.toString());
            const photoIdNum = parseInt(photoId.toString());
            if (photoIdNum < scrambleIdNum) {
                return 0;
            }
            else if (photoIdNum < JmMagicConstants.SCRAMBLE_268850) {
                return 10;
            }
            else {
                const x = photoIdNum < JmMagicConstants.SCRAMBLE_421926 ? 10 : 8;
                const imageNameWithoutExt = this.getFileNameFromUrl(imageName, true);
                const s = `${photoIdNum}${imageNameWithoutExt}`;
                const hash = CryptoJS.MD5(s).toString();
                const lastChar = hash.charCodeAt(hash.length - 1);
                const num = lastChar % x;
                return (num * 2) + 2;
            }
        }
        static getFileNameFromUrl(url, withoutExtension = true) {
            const queryIndex = url.indexOf('?');
            if (queryIndex !== -1) {
                url = url.substring(0, queryIndex);
            }
            const filename = basename(url);
            if (withoutExtension) {
                const lastDotIndex = filename.lastIndexOf('.');
                return lastDotIndex !== -1 ? filename.substring(0, lastDotIndex) : filename;
            }
            return filename;
        }
        static async decodeAndSave(num, imageBase64, decodedSavePath) {
            if (num === 0) {
                await Tools.Files.writeBinary(decodedSavePath, imageBase64);
                return;
            }
            let srcImage = undefined;
            let resultImage = undefined;
            const stripsToRelease = [];
            try {
                srcImage = await Jimp.read(imageBase64);
                const w = await srcImage.getWidth();
                const h = await srcImage.getHeight();
                const over = h % num;
                resultImage = await Jimp.create(w, h);
                for (let i = 0; i < num; i++) {
                    let move = Math.floor(h / num);
                    let ySrc = h - (move * (i + 1)) - over;
                    let yDst = move * i;
                    if (i === 0) {
                        move += over;
                    }
                    else {
                        yDst += over;
                    }
                    if (ySrc < 0 || move <= 0 || (ySrc + move > h))
                        continue;
                    const strip = await srcImage.crop(0, ySrc, w, move);
                    stripsToRelease.push(strip);
                    await resultImage.composite(strip, 0, yDst);
                }
                const decodedImageBase64 = await resultImage.getBase64(Jimp.MIME_JPEG);
                // 移除 "data:image/jpeg;base64," 前缀
                const pureBase64 = decodedImageBase64.substring(decodedImageBase64.indexOf(',') + 1);
                await Tools.Files.writeBinary(decodedSavePath, pureBase64);
            }
            catch (e) {
                console.error(`图片解码失败，将保存原始图片: ${e.message}`);
                await Tools.Files.writeBinary(decodedSavePath, imageBase64);
            }
            finally {
                if (srcImage)
                    await srcImage.release();
                if (resultImage)
                    await resultImage.release();
                for (const strip of stripsToRelease) {
                    await strip.release();
                }
            }
        }
    }
    class JmCryptoTool {
        static md5hex(key) {
            return CryptoJS.MD5(key).toString();
        }
        static tokenAndTokenparam(ts, secret = JmMagicConstants.APP_TOKEN_SECRET) {
            const tokenparam = `${ts},${JmMagicConstants.APP_VERSION}`;
            const token = this.md5hex(`${ts}${secret}`);
            return [token, tokenparam];
        }
        static decodeRespData(data, ts, secret = JmMagicConstants.APP_DATA_SECRET) {
            try {
                // Revert to the standard CryptoJS usage pattern.
                // First, create the key from ts and secret using MD5.
                const keyHex = this.md5hex(`${ts}${secret}`);
                const key = CryptoJS.enc.Hex.parse(keyHex);
                // Then, call decrypt with the standard signature.
                const decrypted = CryptoJS.AES.decrypt(data, key, {
                    mode: CryptoJS.mode.ECB,
                    padding: CryptoJS.pad.Pkcs7
                });
                const decryptedText = decrypted.toString(CryptoJS.enc.Utf8);
                if (!decryptedText) {
                    throw new Error("AES decryption returned an empty result.");
                }
                return decryptedText;
            }
            catch (error) {
                console.error(`Underlying decryption error: ${error.message}`);
                throw new Error(`AES Decryption failed. Original error: ${error.message}`);
            }
        }
    }
    class JmApiResp {
        constructor(resp, ts) {
            this.resp = resp;
            this.ts = ts;
        }
        get isSuccess() {
            return this.resp.isSuccessful();
        }
        get json() {
            try {
                return JSON.parse(this.resp.content);
            }
            catch (error) {
                throw new Error(`JSON解析失败: ${error.message}`);
            }
        }
        get isSuccessful() {
            return this.isSuccess && this.json.code === 200;
        }
        get encodedData() {
            return this.json.data;
        }
        get decodedData() {
            return JmCryptoTool.decodeRespData(this.encodedData, this.ts);
        }
        get resData() {
            if (!this.isSuccessful) {
                throw new Error(`API请求失败: code=${this.json.code}`);
            }
            const decoded = this.decodedData;
            try {
                if (typeof decoded !== 'string' || !decoded) {
                    throw new Error(`Cannot parse non-string or empty value. Type: ${typeof decoded}`);
                }
                return JSON.parse(decoded);
            }
            catch (error) {
                const preview = String(decoded || 'N/A').substring(0, 80);
                throw new Error(`Failed to parse decrypted response. Error: ${error.message}. Original data type was ${typeof decoded}.`);
            }
        }
        get modelData() {
            return this.resData;
        }
    }
    class DirRuleImpl {
        constructor(baseDir) {
            this.baseDir = baseDir;
        }
        decideImageSaveDir(album, photo) {
            return joinPath(this.baseDir, this.sanitize(album.title));
        }
        decideAlbumRootDir(album) {
            return joinPath(this.baseDir, this.sanitize(album.title));
        }
        sanitize(name) {
            return name.replace(/[\\?%*:|"<>]/g, '_');
        }
    }
    class JmOptionImpl {
        constructor(baseDir = "/sdcard/Download/OperitScripts") {
            this.dirRule = new DirRuleImpl(baseDir);
        }
        static default(baseDir = "/sdcard/Download/OperitScripts") {
            return new JmOptionImpl(baseDir);
        }
        buildJmClient() {
            return new JmApiClientImpl();
        }
    }
    class JmApiClientImpl {
        constructor() {
            this.domainList = JmModuleConfig.DOMAIN_API_LIST;
            this.retryTimes = 3;
            this.client = OkHttp.newClient();
            this.API_ALBUM = '/album';
            this.API_CHAPTER = '/chapter';
            this.API_SEARCH = '/search';
            this.API_CATEGORIES_FILTER = '/categories/filter';
        }
        async getAlbumDetail(albumId) {
            const resp = await this.reqApi(`${this.API_ALBUM}?id=${albumId}`);
            const data = resp.resData;
            if (!data || !data.name)
                throw new Error(`本子 ${albumId} 不存在或数据无效`);
            return this.parseAlbumData(albumId, data);
        }
        async getPhotoDetail(photoId) {
            const resp = await this.reqApi(`${this.API_CHAPTER}?id=${photoId}`);
            const data = resp.resData;
            if (!data || !data.name)
                throw new Error(`章节 ${photoId} 不存在或数据无效`);
            return this.parsePhotoData(photoId, data);
        }
        async searchComics(params) {
            const { query, page = 1, order_by = 'view', time = 'all' } = params;
            const orderMap = { 'latest': 'mr', 'view': 'mv', 'picture': 'mp', 'like': 'tf' };
            const timeMap = { 'today': 't', 'week': 'w', 'month': 'm', 'all': 'a' };
            const apiParams = {
                search_query: query,
                page,
                o: orderMap[order_by.toLowerCase()] || orderMap['view'],
                t: timeMap[time.toLowerCase()] || timeMap['all']
            };
            const resp = await this.reqApi(`${this.API_SEARCH}?${this.toUrlSearchParams(apiParams)}`);
            const data = resp.resData;
            const results = (data.content || []).map((item) => ({
                id: String(item.id || item.album_id),
                title: item.name || item.title
            }));
            return {
                search_params: params,
                results: results,
                total_results: results.length,
            };
        }
        async reqApi(url, method = 'GET', data) {
            const ts = Math.floor(Date.now() / 1000);
            for (let i = 0; i < this.domainList.length; i++) {
                const domain = this.domainList[i];
                for (let retry = 0; retry < this.retryTimes; retry++) {
                    try {
                        const fullUrl = `${JmModuleConfig.PROT}${domain}${url}`;
                        const [token, tokenparam] = JmCryptoTool.tokenAndTokenparam(ts);
                        const headers = Object.assign(Object.assign({}, JmModuleConfig.APP_HEADERS_TEMPLATE), { token, tokenparam });
                        const requestBuilder = this.client.newRequest().url(fullUrl).headers(headers);
                        if (method === 'POST') {
                            requestBuilder.method('POST').jsonBody(data);
                        }
                        const resp = await requestBuilder.build().execute();
                        if (resp.isSuccessful()) {
                            return new JmApiResp(resp, ts);
                        }
                    }
                    catch (error) {
                        console.log(`[API] 请求失败: ${error.message} 域名: ${domain}`);
                        if (retry === this.retryTimes - 1 && i === this.domainList.length - 1) {
                            throw new Error(`所有域名和重试都失败: ${error.message}`);
                        }
                    }
                }
            }
            throw new Error('请求失败');
        }
        async downloadImage(imageUrl, savePath, scrambleId, photoId) {
            try {
                const response = await this.client.newRequest().url(imageUrl).headers(JmModuleConfig.APP_HEADERS_IMAGE).build().execute();
                if (!response.isSuccessful()) {
                    throw new Error(`HTTP error! status: ${response.statusCode}`);
                }
                const imageBase64 = response.bodyAsBase64();
                const dir = dirname(savePath);
                await ensureDirExists(dir);
                const imageName = JmImageTool.getFileNameFromUrl(imageUrl, false);
                const num = JmImageTool.getNum(scrambleId, photoId, imageName);
                await JmImageTool.decodeAndSave(num, imageBase64, savePath);
                return true;
            }
            catch (error) {
                console.error(`[图片] 下载失败: ${imageUrl}, 错误: ${error.message}`);
                return false;
            }
        }
        toUrlSearchParams(obj) {
            return Object.keys(obj).map(k => `${encodeURIComponent(k)}=${encodeURIComponent(obj[k])}`).join('&');
        }
        parseAlbumData(albumId, data) {
            const episodeList = data.series && data.series.length > 0 ? data.series : [{ id: albumId, title: data.name }];
            return {
                id: albumId,
                title: data.name || `本子 ${albumId}`,
                author: (data.author && data.author[0]) || '未知作者',
                episodeList: episodeList,
                scrambleId: data.scramble_id || JmMagicConstants.SCRAMBLE_220980,
                length: episodeList.length,
            };
        }
        parsePhotoData(photoId, data) {
            return {
                id: photoId,
                title: data.name || `章节 ${photoId}`,
                pageArr: data.images || [],
                albumId: data.album_id || photoId,
                scrambleId: data.scramble_id || JmMagicConstants.SCRAMBLE_220980,
                length: (data.images || []).length,
            };
        }
    }
    class JmDownloaderImpl {
        constructor(option) {
            this.option = option;
            this.client = option.buildJmClient();
        }
        async downloadAlbum(albumId) {
            const album = await this.client.getAlbumDetail(albumId);
            await this.downloadByAlbumDetail(album);
            return album;
        }
        async downloadByAlbumDetail(album) {
            const albumDir = this.option.dirRule.decideAlbumRootDir(album);
            await ensureDirExists(albumDir);
            console.log(`[专辑: ${album.title}] 发现 ${album.episodeList.length} 个章节, 开始下载...`);
            const chapterConcurrency = 5;
            const tasks = album.episodeList.map((episode, i) => async () => {
                console.log(`  [章节 ${i + 1}/${album.episodeList.length}] 开始下载: ${episode.title} (${episode.id})`);
                try {
                    const photo = await this.client.getPhotoDetail(episode.id);
                    await this.downloadPhotoImages(photo, albumDir, album.id);
                    console.log(`  [章节 ${i + 1}/${album.episodeList.length}] 下载完成: ${episode.title}`);
                }
                catch (e) {
                    console.error(`  [章节 ${i + 1}/${album.episodeList.length}] 下载失败: ${episode.title}, 错误: ${e.message}`);
                }
            });
            await runTasksWithConcurrency(tasks, chapterConcurrency);
        }
        async downloadPhotoImages(photo, albumDir, albumId) {
            if (!photo.pageArr || photo.pageArr.length === 0)
                return;
            console.log(`    [图片集: ${photo.title}] 发现 ${photo.pageArr.length} 张图片, 开始下载...`);
            const concurrencyLimit = 10;
            const tasks = photo.pageArr.map((imageName, i) => {
                return async () => {
                    const finalFileName = `${(i + 1).toString().padStart(5, '0')}.jpg`;
                    const filePath = joinPath(albumDir, finalFileName);
                    const fileExists = await Tools.Files.exists(filePath);
                    if (fileExists.exists) {
                        return;
                    }
                    const imageUrl = this.buildImageUrl(photo, imageName);
                    try {
                        await this.client.downloadImage(imageUrl, filePath, photo.scrambleId, photo.id);
                    }
                    catch (e) {
                        console.error(`      [图片下载失败] ${finalFileName} from ${photo.title}: ${e.message}`);
                    }
                };
            });
            await runTasksWithConcurrency(tasks, concurrencyLimit);
            console.log(`    [图片集: ${photo.title}] 所有图片下载任务已处理。`);
        }
        getFileExtension(filename) {
            const dotIndex = filename.lastIndexOf('.');
            return dotIndex > 0 ? filename.substring(dotIndex + 1) : 'jpg';
        }
        buildImageUrl(photo, imageName) {
            const domain = JmModuleConfig.DOMAIN_IMAGE_LIST[Math.floor(Math.random() * JmModuleConfig.DOMAIN_IMAGE_LIST.length)];
            return `${JmModuleConfig.PROT}${domain}/media/photos/${photo.albumId}/${imageName}`;
        }
    }
    class SimpleJMDownloader {
        constructor(downloadDir = "/sdcard/Download/OperitScripts") {
            this.option = JmOptionImpl.default(downloadDir);
            this.downloader = new JmDownloaderImpl(this.option);
            this.client = this.option.buildJmClient();
            console.log(`✅ JM下载器初始化成功, 下载目录: ${this.option.dirRule.baseDir}`);
        }
        async searchComics(params) {
            console.log(`🔍 搜索漫画: ${params.query}`);
            return await this.client.searchComics(params);
        }
        async getAlbumInfo(albumId) {
            const album = await this.client.getAlbumDetail(albumId);
            return {
                id: album.id,
                title: album.title,
                author: album.author,
                chapterCount: album.length,
                success: true
            };
        }
        async downloadAlbum(albumId) {
            console.log(`📖 获取本子信息: ${albumId}`);
            try {
                const info = await this.getAlbumInfo(albumId);
                if (!info.success)
                    return { success: false, albumId, error: "获取信息失败" };
                console.log(`📥 开始下载本子: ${info.title}`);
                await this.downloader.downloadAlbum(albumId);
                const downloadedFiles = await this._checkDownloadedFiles(info.title);
                return {
                    success: true,
                    albumId: albumId,
                    title: info.title,
                    downloadedFiles
                };
            }
            catch (error) {
                return { success: false, albumId, error: error.message };
            }
        }
        async batchDownload(albumIds) {
            const results = [];
            console.log(`📦 开始批量下载 ${albumIds.length} 个本子`);
            const concurrencyLimit = 3; // 限制并发下载的漫画数量
            const tasks = albumIds.map((albumId, i) => async () => {
                console.log(`\n[${i + 1}/${albumIds.length}] 开始处理本子: ${albumId}`);
                const result = await this.downloadAlbum(albumId);
                if (result.success) {
                    console.log(`✅ [${i + 1}/${albumIds.length}] 下载成功: ${result.title}`);
                }
                else {
                    console.log(`❌ [${i + 1}/${albumIds.length}] 下载失败: ${albumId}, ${result.error || 'Unknown error'}`);
                }
                return result;
            });
            return await runTasksWithConcurrency(tasks, concurrencyLimit);
        }
        async _checkDownloadedFiles(title) {
            const albumDir = this.option.dirRule.decideAlbumRootDir({ title });
            const dirExists = await Tools.Files.exists(albumDir);
            if (dirExists.exists) {
                const listResult = await Tools.Files.list(albumDir);
                const files = listResult.entries.map(e => e.name);
                return {
                    directory: albumDir,
                    fileCount: files.length,
                    files: files.slice(0, 10)
                };
            }
            return { directory: undefined, fileCount: 0, files: [] };
        }
    }
    //endregion
    //region Tool Implementations
    async function main() {
        console.log("🚀 开始执行JMComic工具功能测试...");
        const downloader = new SimpleJMDownloader("/sdcard/Download/OperitScripts/test_downloads");
        const testQuery = "原神";
        console.log(`1. 测试搜索功能，关键词: "${testQuery}"`);
        const searchResult = await downloader.searchComics({ query: testQuery });
        if (!searchResult || !searchResult.results || searchResult.results.length === 0) {
            throw new Error(`搜索测试失败: 未能找到关于 "${testQuery}" 的任何结果。`);
        }
        console.log(`✅ 搜索成功, 找到 ${searchResult.total_results} 个结果。`);
        const firstAlbum = searchResult.results[0];
        console.log(`2. 测试获取作品信息功能, 作品ID: ${firstAlbum.id} (${firstAlbum.title})`);
        const albumInfo = await downloader.getAlbumInfo(firstAlbum.id);
        if (!albumInfo || !albumInfo.success) {
            throw new Error(`获取作品信息失败, ID: ${firstAlbum.id}`);
        }
        console.log(`✅ 作品信息获取成功:`);
        console.log(`   - 标题: ${albumInfo.title}`);
        console.log(`   - 作者: ${albumInfo.author}`);
        console.log(`   - 章节数: ${albumInfo.chapterCount}`);
        console.log(`3. 测试下载功能, 作品ID: ${firstAlbum.id} (${firstAlbum.title})`);
        const downloadResult = await downloader.downloadAlbum(firstAlbum.id);
        if (!downloadResult || !downloadResult.success) {
            throw new Error(`下载作品失败, ID: ${firstAlbum.id}`);
        }
        console.log(`✅ 下载成功:`);
        console.log(`   - 保存目录: ${downloadResult.downloadedFiles.directory}`);
        console.log(`   - 文件数量: ${downloadResult.downloadedFiles.fileCount}`);
        const summary = `JMComic工具测试完成。成功搜索、获取信息并下载了作品《${albumInfo.title}》。`;
        console.log(`\n${summary}`);
        return summary;
    }
    async function search_comics(params) {
        const downloader = new SimpleJMDownloader();
        return await downloader.searchComics(params);
    }
    async function get_album_info(params) {
        const downloader = new SimpleJMDownloader();
        return await downloader.getAlbumInfo(params.album_id);
    }
    async function download_album(params) {
        const downloader = new SimpleJMDownloader(params.download_dir);
        return await downloader.downloadAlbum(params.album_id);
    }
    async function batch_download_albums(params) {
        const albumIds = params.album_ids.split(',').map(id => id.trim()).filter(id => id);
        if (albumIds.length === 0)
            throw new Error("album_ids不能为空");
        const downloader = new SimpleJMDownloader(params.download_dir);
        return await downloader.batchDownload(albumIds);
    }
    async function jmcomic_wrap(func, params, successMessage, failMessage) {
        try {
            console.log(`开始执行: ${func.name}`);
            const result = await func(params);
            complete({ success: true, message: successMessage, data: result });
        }
        catch (error) {
            console.error(`${func.name} 执行失败: ${error.message}`);
            complete({ success: false, message: `${failMessage}: ${error.message}`, error_stack: error.stack });
        }
    }
    //endregion
    return {
        main: (p) => jmcomic_wrap(main, p, '功能测试完成', '功能测试失败'),
        search_comics: (p) => jmcomic_wrap(search_comics, p, '搜索完成', '搜索失败'),
        get_album_info: (p) => jmcomic_wrap(get_album_info, p, '信息获取完成', '信息获取失败'),
        download_album: (p) => jmcomic_wrap(download_album, p, '下载完成', '下载失败'),
        batch_download_albums: (p) => jmcomic_wrap(batch_download_albums, p, '批量下载完成', '批量下载失败'),
    };
})();
exports.main = jmcomic.main;
exports.search_comics = jmcomic.search_comics;
exports.get_album_info = jmcomic.get_album_info;
exports.download_album = jmcomic.download_album;
exports.batch_download_albums = jmcomic.batch_download_albums;
