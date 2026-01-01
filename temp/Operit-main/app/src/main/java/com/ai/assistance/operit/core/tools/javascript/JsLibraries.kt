package com.ai.assistance.operit.core.tools.javascript

/** JavaScript 第三方库定义 包含常用的第三方JavaScript库，如Lodash核心功能和数据处理工具 */
fun getJsThirdPartyLibraries(): String {
    return """
        // Lodash 核心功能
        // 轻量级版本，包含最常用的工具函数
        var _ = (function() {
            // 简单的 Lodash 核心实现
            return {
                isEmpty: function(value) {
                    return value === null || value === undefined || 
                           (Array.isArray(value) && value.length === 0) ||
                           (typeof value === 'object' && Object.keys(value).length === 0);
                },
                isString: function(value) {
                    return typeof value === 'string';
                },
                isNumber: function(value) {
                    return typeof value === 'number' && !isNaN(value);
                },
                isBoolean: function(value) {
                    return typeof value === 'boolean';
                },
                isObject: function(value) {
                    return typeof value === 'object' && value !== null && !Array.isArray(value);
                },
                isArray: function(value) {
                    return Array.isArray(value);
                },
                forEach: function(collection, iteratee) {
                    if (Array.isArray(collection)) {
                        for (let i = 0; i < collection.length; i++) {
                            iteratee(collection[i], i, collection);
                        }
                    } else if (typeof collection === 'object' && collection !== null) {
                        for (let key in collection) {
                            if (collection.hasOwnProperty(key)) {
                                iteratee(collection[key], key, collection);
                            }
                        }
                    }
                    return collection;
                },
                map: function(collection, iteratee) {
                    const result = [];
                    if (Array.isArray(collection)) {
                        for (let i = 0; i < collection.length; i++) {
                            result.push(iteratee(collection[i], i, collection));
                        }
                    } else if (typeof collection === 'object' && collection !== null) {
                        for (let key in collection) {
                            if (collection.hasOwnProperty(key)) {
                                result.push(iteratee(collection[key], key, collection));
                            }
                        }
                    }
                    return result;
                }
            };
        })();
        
        // 简单的数据处理库
        var dataUtils = {
            parseJson: function(jsonString) {
                try {
                    return JSON.parse(jsonString);
                } catch (e) {
                    return null;
                }
            },
            stringifyJson: function(obj) {
                try {
                    return JSON.stringify(obj);
                } catch (e) {
                    return "{}";
                }
            },
            formatDate: function(date) {
                if (!date) date = new Date();
                if (typeof date === 'string') date = new Date(date);
                
                return date.getFullYear() + '-' + 
                       String(date.getMonth() + 1).padStart(2, '0') + '-' + 
                       String(date.getDate()).padStart(2, '0') + ' ' + 
                       String(date.getHours()).padStart(2, '0') + ':' + 
                       String(date.getMinutes()).padStart(2, '0') + ':' + 
                       String(date.getSeconds()).padStart(2, '0');
            }
        };
    """.trimIndent()
}
