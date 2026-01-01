//
//  mnnllmnative.cpp
//  MNN LLM Engine JNI wrapper
//
//  Based on MnnLlmChat official implementation
//

#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <android/log.h>
#include <fstream>
#include <sstream>
#include <map>
#include <mutex>

// MNN LLM headers
#include <MNN/expr/Expr.hpp>
#include <MNN/expr/Module.hpp>
#include <llm/llm.hpp>

#define TAG "MNNLlmNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

using namespace MNN;
using namespace MNN::Transformer;

// =======================
// Cancellation Support
// =======================

// 全局取消标志映射 (llmPtr -> shouldCancel)
static std::mutex gCancelMutex;
static std::map<jlong, bool> gCancelFlags;

// 设置取消标志
void setCancelFlag(jlong llmPtr, bool value) {
    std::lock_guard<std::mutex> lock(gCancelMutex);
    gCancelFlags[llmPtr] = value;
}

// 检查取消标志
bool checkCancelFlag(jlong llmPtr) {
    std::lock_guard<std::mutex> lock(gCancelMutex);
    auto it = gCancelFlags.find(llmPtr);
    return (it != gCancelFlags.end()) && it->second;
}

// 清除取消标志
void clearCancelFlag(jlong llmPtr) {
    std::lock_guard<std::mutex> lock(gCancelMutex);
    gCancelFlags.erase(llmPtr);
}

// =======================
// Helper Functions
// =======================

std::string jstringToString(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return "";
    const char* cstr = env->GetStringUTFChars(jstr, nullptr);
    std::string result(cstr);
    env->ReleaseStringUTFChars(jstr, cstr);
    return result;
}

jstring stringToJstring(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

// =======================
// LLM Instance Management
// =======================

extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_mnn_MNNLlmNative_nativeCreateLlm(
    JNIEnv* env, jclass clazz, jstring jconfigPath) {
    
    std::string configPath = jstringToString(env, jconfigPath);
    LOGD("Creating LLM from config: %s", configPath.c_str());
    
    try {
        // 使用 MNN LLM 引擎创建实例（但不加载）
        // 按照官方 llm_session.cpp 的做法，先创建实例
        Llm* llm = Llm::createLLM(configPath);
        if (llm == nullptr) {
            LOGE("Failed to create LLM instance");
            return 0;
        }
        
        // 注意：这里不调用 load()，让上层在设置完配置后再调用 load()
        // 这是关键：配置必须在 load() 之前设置！
        LOGI("LLM instance created at %p (not loaded yet)", llm);
        return reinterpret_cast<jlong>(llm);
        
    } catch (const std::exception& e) {
        LOGE("Exception creating LLM: %s", e.what());
        return 0;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_mnn_MNNLlmNative_nativeLoadLlm(
    JNIEnv* env, jclass clazz, jlong llmPtr) {
    
    if (llmPtr == 0) return JNI_FALSE;
    
    Llm* llm = reinterpret_cast<Llm*>(llmPtr);
    LOGD("Loading LLM model at %p", llm);
    
    try {
        // 加载模型（必须在配置设置之后调用）
        if (!llm->load()) {
            LOGE("Failed to load LLM model");
            return JNI_FALSE;
        }
        
        LOGI("LLM model loaded successfully");
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("Exception loading LLM: %s", e.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_mnn_MNNLlmNative_nativeReleaseLlm(
    JNIEnv* env, jclass clazz, jlong llmPtr) {
    
    if (llmPtr == 0) return;
    
    Llm* llm = reinterpret_cast<Llm*>(llmPtr);
    LOGD("Releasing LLM at %p", llm);
    
    try {
        Llm::destroy(llm);
        LOGI("LLM released successfully");
    } catch (const std::exception& e) {
        LOGE("Exception releasing LLM: %s", e.what());
    }
}

// =======================
// Tokenization
// =======================

extern "C" JNIEXPORT jintArray JNICALL
Java_com_ai_assistance_mnn_MNNLlmNative_nativeTokenize(
    JNIEnv* env, jclass clazz, jlong llmPtr, jstring jtext) {
    
    if (llmPtr == 0) return nullptr;
    
    Llm* llm = reinterpret_cast<Llm*>(llmPtr);
    std::string text = jstringToString(env, jtext);
    
    try {
        // 使用 LLM 的 tokenizer 编码
        std::vector<int> tokens = llm->tokenizer_encode(text);
        
        // 转换为 Java int array
        jintArray result = env->NewIntArray(tokens.size());
        if (result != nullptr) {
            env->SetIntArrayRegion(result, 0, tokens.size(), tokens.data());
        }
        
        return result;
        
    } catch (const std::exception& e) {
        LOGE("Exception in tokenize: %s", e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mnn_MNNLlmNative_nativeDetokenize(
    JNIEnv* env, jclass clazz, jlong llmPtr, jint token) {
    
    if (llmPtr == 0) return nullptr;
    
    Llm* llm = reinterpret_cast<Llm*>(llmPtr);
    
    try {
        // 使用 LLM 的 tokenizer 解码
        std::string text = llm->tokenizer_decode(token);
        return stringToJstring(env, text);
        
    } catch (const std::exception& e) {
        LOGE("Exception in detokenize: %s", e.what());
        return nullptr;
    }
}

// =======================
// Text Generation (Streaming)
// =======================

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mnn_MNNLlmNative_nativeGenerate(
    JNIEnv* env, jclass clazz, 
    jlong llmPtr, 
    jstring jprompt,
    jint maxTokens,
    jobject callback) {
    
    if (llmPtr == 0) return nullptr;
    
    Llm* llm = reinterpret_cast<Llm*>(llmPtr);
    std::string prompt = jstringToString(env, jprompt);
    
    try {
        // 获取 callback 方法
        jclass callbackClass = env->GetObjectClass(callback);
        jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");
        
        if (onTokenMethod == nullptr) {
            LOGE("Failed to find onToken method in callback");
            return nullptr;
        }
        
        // 编码输入
        std::vector<int> inputTokens = llm->tokenizer_encode(prompt);
        LOGD("Input tokens: %zu", inputTokens.size());
        
        // 生成输出（使用流式输出）
        std::stringstream outputStream;
        llm->response(inputTokens, &outputStream, nullptr, maxTokens);
        
        // 返回完整响应
        std::string response = outputStream.str();
        LOGD("Generated response: %zu chars", response.size());
        
        return stringToJstring(env, response);
        
    } catch (const std::exception& e) {
        LOGE("Exception in generate: %s", e.what());
        return nullptr;
    }
}

// =======================
// Streaming Generation with Callback
// =======================

struct StreamContext {
    JavaVM* jvm;
    jobject callbackGlobalRef;
    jmethodID onTokenMethod;
    std::string buffer;
    bool shouldStop = false;
    jlong llmPtr = 0;  // 添加 llm 指针用于检查取消标志
};

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_mnn_MNNLlmNative_nativeGenerateStream(
    JNIEnv* env, jclass clazz,
    jlong llmPtr,
    jobject jhistory,
    jint maxTokens,
    jobject callback) {
    
    if (llmPtr == 0) return JNI_FALSE;
    
    Llm* llm = reinterpret_cast<Llm*>(llmPtr);
    
    // 解析历史记录 List<Pair<String, String>>
    std::vector<std::pair<std::string, std::string>> history;
    
    jclass listClass = env->FindClass("java/util/List");
    jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
    jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");
    jint listSize = env->CallIntMethod(jhistory, sizeMethod);
    
    jclass pairClass = env->FindClass("kotlin/Pair");
    jmethodID getFirstMethod = env->GetMethodID(pairClass, "getFirst", "()Ljava/lang/Object;");
    jmethodID getSecondMethod = env->GetMethodID(pairClass, "getSecond", "()Ljava/lang/Object;");
    
    for (jint i = 0; i < listSize; i++) {
        jobject pairObj = env->CallObjectMethod(jhistory, getMethod, i);
        if (pairObj == nullptr) continue;
        
        jobject roleObj = env->CallObjectMethod(pairObj, getFirstMethod);
        jobject contentObj = env->CallObjectMethod(pairObj, getSecondMethod);
        
        if (roleObj != nullptr && contentObj != nullptr) {
            std::string role = jstringToString(env, (jstring)roleObj);
            std::string content = jstringToString(env, (jstring)contentObj);
            history.emplace_back(role, content);
        }
        
        if (roleObj) env->DeleteLocalRef(roleObj);
        if (contentObj) env->DeleteLocalRef(contentObj);
        env->DeleteLocalRef(pairObj);
    }
    
    env->DeleteLocalRef(listClass);
    env->DeleteLocalRef(pairClass);
    
    LOGD("Starting stream generation with %zu history messages", history.size());
    
    jobject callbackGlobalRef = nullptr;
    
    try {
        // 获取 JavaVM
        JavaVM* jvm = nullptr;
        if (env->GetJavaVM(&jvm) != JNI_OK || jvm == nullptr) {
            LOGE("Failed to get JavaVM");
            return JNI_FALSE;
        }
        
        // 获取 callback 方法
        jclass callbackClass = env->GetObjectClass(callback);
        jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(callbackClass);
        
        if (onTokenMethod == nullptr) {
            LOGE("Failed to find onToken method in callback");
            return JNI_FALSE;
        }
        
        // 创建全局引用，可以跨线程使用
        callbackGlobalRef = env->NewGlobalRef(callback);
        if (callbackGlobalRef == nullptr) {
            LOGE("Failed to create global reference for callback");
            return JNI_FALSE;
        }
        
        // 准备流式输出上下文
        StreamContext context;
        context.jvm = jvm;
        context.callbackGlobalRef = callbackGlobalRef;
        context.onTokenMethod = onTokenMethod;
        context.llmPtr = llmPtr;
        
        // 清除之前的取消标志
        setCancelFlag(llmPtr, false);
        
        // 创建自定义 ostream 来捕获输出（仿照 MNN 官方 LlmStreamBuffer 实现）
        class CallbackStream : public std::streambuf {
        public:
            CallbackStream(StreamContext* ctx) : mContext(ctx) {}
            
            // 公开方法，用于最后刷新缓冲区
            void flushToCallback() {
                if (mContext->buffer.empty() || mContext->shouldStop) {
                    return;
                }
                
                // 获取当前线程的 JNIEnv
                bool needDetach = false;
                JNIEnv* env = nullptr;
                
                int getEnvResult = mContext->jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
                if (getEnvResult == JNI_EDETACHED) {
                    // 当前线程未附加到 JVM，需要附加
                    if (mContext->jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to attach thread");
                        return;
                    }
                    needDetach = true;
                } else if (getEnvResult != JNI_OK) {
                    __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to get JNIEnv: %d", getEnvResult);
                    return;
                }
                
                try {
                    // 创建 Java 字符串并调用 callback
                    jstring jtoken = env->NewStringUTF(mContext->buffer.c_str());
                    if (jtoken != nullptr) {
                        jboolean shouldContinue = env->CallBooleanMethod(
                            mContext->callbackGlobalRef,
                            mContext->onTokenMethod,
                            jtoken
                        );
                        env->DeleteLocalRef(jtoken);
                        
                        // 检查是否有 JNI 异常
                        if (env->ExceptionCheck()) {
                            env->ExceptionDescribe();
                            env->ExceptionClear();
                            mContext->shouldStop = true;
                        } else if (!shouldContinue) {
                            mContext->shouldStop = true;
                            __android_log_print(ANDROID_LOG_DEBUG, TAG, "Stream stopped by callback");
                        }
                    } else {
                        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to create jstring");
                    }
                } catch (...) {
                    __android_log_print(ANDROID_LOG_ERROR, TAG, "Exception in callback");
                    mContext->shouldStop = true;
                }
                
                // 如果需要，分离线程
                if (needDetach) {
                    mContext->jvm->DetachCurrentThread();
                }
                
                mContext->buffer.clear();
            }
            
        protected:
            // 只重写 xsputn，不重写 overflow
            virtual std::streamsize xsputn(const char* s, std::streamsize n) override {
                // 检查取消标志或 shouldStop
                if (mContext->shouldStop || checkCancelFlag(mContext->llmPtr) || n <= 0) {
                    if (checkCancelFlag(mContext->llmPtr)) {
                        __android_log_print(ANDROID_LOG_DEBUG, TAG, "Generation cancelled by user");
                        mContext->shouldStop = true;
                    }
                    return 0;
                }
                
                // 累积字符到缓冲区
                mContext->buffer.append(s, n);
                
                // 检查是否应该刷新缓冲区
                // 策略：累积到一定大小或遇到分隔符
                bool shouldFlush = false;
                if (mContext->buffer.size() >= 16) {  // 增加阈值以减少 JNI 调用频率
                    shouldFlush = true;
                } else {
                    // 检查最后几个字符是否包含分隔符（ASCII 字符）
                    for (std::streamsize i = 0; i < n && !shouldFlush; ++i) {
                        char ch = s[i];
                        if (ch == '\n' || ch == '.' || ch == '!' || ch == '?') {
                            shouldFlush = true;
                        }
                    }
                    // 检查中文标点（UTF-8 多字节序列）
                    if (!shouldFlush && mContext->buffer.size() >= 3) {
                        const char* bufEnd = mContext->buffer.c_str() + mContext->buffer.size();
                        // UTF-8 中文句号：E3 80 82 (。)
                        // UTF-8 中文感叹号：EF BC 81 (！)
                        // UTF-8 中文问号：EF BC 9F (？)
                        if (mContext->buffer.size() >= 3) {
                            unsigned char c1 = static_cast<unsigned char>(*(bufEnd - 3));
                            unsigned char c2 = static_cast<unsigned char>(*(bufEnd - 2));
                            unsigned char c3 = static_cast<unsigned char>(*(bufEnd - 1));
                            if ((c1 == 0xE3 && c2 == 0x80 && c3 == 0x82) ||  // 。
                                (c1 == 0xEF && c2 == 0xBC && c3 == 0x81) ||  // ！
                                (c1 == 0xEF && c2 == 0xBC && c3 == 0x9F)) {  // ？
                                shouldFlush = true;
                            }
                        }
                    }
                }
                
                if (shouldFlush) {
                    flushToCallback();
                }
                
                return n;
            }
            
        private:
            StreamContext* mContext;
        };
        
        CallbackStream callbackBuf(&context);
        std::ostream outputStream(&callbackBuf);
        
        // 执行生成（使用历史记录，让 LLM 内部自动应用 chat template）
        llm->response(history, &outputStream, nullptr, maxTokens);
        
        // 刷新剩余缓冲区（使用 callbackBuf 的方法以确保线程安全）
        if (!context.buffer.empty() && !context.shouldStop) {
            callbackBuf.flushToCallback();
        }
        
        // 清理全局引用和取消标志
        if (callbackGlobalRef != nullptr) {
            env->DeleteGlobalRef(callbackGlobalRef);
        }
        clearCancelFlag(llmPtr);
        
        LOGI("Stream generation completed");
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("Exception in generateStream: %s", e.what());
        if (callbackGlobalRef != nullptr) {
            env->DeleteGlobalRef(callbackGlobalRef);
        }
        clearCancelFlag(llmPtr);
        return JNI_FALSE;
    } catch (...) {
        LOGE("Unknown exception in generateStream");
        if (callbackGlobalRef != nullptr) {
            env->DeleteGlobalRef(callbackGlobalRef);
        }
        clearCancelFlag(llmPtr);
        return JNI_FALSE;
    }
}

// =======================
// Cancel Generation
// =======================

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_mnn_MNNLlmNative_nativeCancel(
    JNIEnv* env, jclass clazz, jlong llmPtr) {
    
    if (llmPtr == 0) return;
    
    LOGD("Cancelling generation for LLM at %p", reinterpret_cast<void*>(llmPtr));
    setCancelFlag(llmPtr, true);
    LOGI("Cancellation flag set for LLM");
}

// =======================
// Chat Template
// =======================

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mnn_MNNLlmNative_nativeApplyChatTemplate(
    JNIEnv* env, jclass clazz,
    jlong llmPtr,
    jstring juserContent) {
    
    if (llmPtr == 0) return nullptr;
    
    Llm* llm = reinterpret_cast<Llm*>(llmPtr);
    std::string userContent = jstringToString(env, juserContent);
    
    try {
        std::string templated = llm->apply_chat_template(userContent);
        return stringToJstring(env, templated);
    } catch (const std::exception& e) {
        LOGE("Exception in applyChatTemplate: %s", e.what());
        return nullptr;
    }
}

// =======================
// Reset
// =======================

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_mnn_MNNLlmNative_nativeReset(
    JNIEnv* env, jclass clazz, jlong llmPtr) {
    
    if (llmPtr == 0) return;
    
    Llm* llm = reinterpret_cast<Llm*>(llmPtr);
    
    try {
        llm->reset();
        LOGD("LLM reset successfully");
    } catch (const std::exception& e) {
        LOGE("Exception in reset: %s", e.what());
    }
}

// =======================
// Set Config
// =======================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_mnn_MNNLlmNative_nativeSetConfig(
    JNIEnv* env, jclass clazz, jlong llmPtr, jstring jconfigJson) {
    
    if (llmPtr == 0) return JNI_FALSE;
    
    Llm* llm = reinterpret_cast<Llm*>(llmPtr);
    std::string configJson = jstringToString(env, jconfigJson);
    
    try {
        bool success = llm->set_config(configJson);
        if (success) {
            LOGD("LLM config set successfully");
        } else {
            LOGE("Failed to set LLM config");
        }
        return success ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        LOGE("Exception in set_config: %s", e.what());
        return JNI_FALSE;
    }
}

