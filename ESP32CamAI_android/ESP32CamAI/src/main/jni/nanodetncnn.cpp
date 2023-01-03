// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <android/bitmap.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>
#include <list>

#include <platform.h>
#include <benchmark.h>
#include <thread>

#include "nanodet.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

static int draw_unsupported(cv::Mat& rgb)
{
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static int draw_fps(cv::Mat& rgb)
{
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f)
        {
            t0 = t1;
            return 0;
        }

        float fps = 1000.f / (t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--)
        {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        if (fps_history[9] == 0.f)
        {
            return 0;
        }

        for (int i = 0; i < 10; i++)
        {
            avg_fps += fps_history[i];
        }
        avg_fps /= 10.f;
    }

    char text[32];
    sprintf(text, "FPS=%.2f", avg_fps);

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

    int y = 0;
    int x = rgb.cols - label_size.width;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));

    return 0;
}

static NanoDet* g_nanodet = 0;
static ncnn::Mutex lock;
static std::vector<cv::Mat> g_input;
static std::vector<std::vector<Object>> g_output;
static bool g_start = true;
static ncnn::Thread* g_pworker = 0;

static void* process(void*) {
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "process thread start");

    while(g_start) {
        if (g_input.empty()) {
            std::this_thread::sleep_for(std::chrono::milliseconds (1000));
            continue;
        }
        cv::Mat rgb;
        {
            ncnn::MutexLockGuard g(lock);
            rgb = g_input[g_input.size() - 1];
            g_input.clear();
        }

        if (g_nanodet) {
            std::vector<Object> objects;
            g_nanodet->detect(rgb, objects);
            {
                ncnn::MutexLockGuard g(lock);
                if (g_output.size() > 100) {
                    g_output.clear();
                }
                g_output.emplace_back(objects);
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds (10));
        __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "process thread %p", g_nanodet);
    }
    return nullptr;
}

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");

    g_pworker = new ncnn::Thread(process);

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        g_start = false;
        g_pworker->join();
        delete g_pworker;
        g_pworker = 0;

        delete g_nanodet;
        g_nanodet = 0;
    }
}

// public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
JNIEXPORT jboolean JNICALL Java_com_p4f_esp32camai_NanoDetNcnn_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint cpugpu)
{
    if (cpugpu < 0 || cpugpu > 1)
    {
        return JNI_FALSE;
    }
    g_start = true;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    const char* modeltypes[] =
    {
        "ELite0_320"
    };

    const int target_sizes[] =
    {
        320,
        416,
        416,
        320,
        416,
        512,
        416
    };

    const float mean_vals[][3] =
    {
        {103.53f, 116.28f, 123.675f},
        {103.53f, 116.28f, 123.675f},
        {103.53f, 116.28f, 123.675f},
        {127.f, 127.f, 127.f},
        {127.f, 127.f, 127.f},
        {127.f, 127.f, 127.f},
        {103.53f, 116.28f, 123.675f}
    };

    const float norm_vals[][3] =
    {
        {1.f / 57.375f, 1.f / 57.12f, 1.f / 58.395f},
        {1.f / 57.375f, 1.f / 57.12f, 1.f / 58.395f},
        {1.f / 57.375f, 1.f / 57.12f, 1.f / 58.395f},
        {1.f / 128.f, 1.f / 128.f, 1.f / 128.f},
        {1.f / 128.f, 1.f / 128.f, 1.f / 128.f},
        {1.f / 128.f, 1.f / 128.f, 1.f / 128.f},
        {1.f / 57.375f, 1.f / 57.12f, 1.f / 58.395f}
    };

    const char* modeltype = modeltypes[0];
    int target_size = target_sizes[0];
    bool use_gpu = (int)cpugpu == 1;

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        if (use_gpu && ncnn::get_gpu_count() == 0)
        {
            // no gpu
            delete g_nanodet;
            g_nanodet = 0;
        }
        else
        {
            if (!g_nanodet)
                g_nanodet = new NanoDet;
            g_nanodet->load(mgr, modeltype, target_size, mean_vals[0], norm_vals[0], use_gpu);
        }
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_p4f_esp32camai_NanoDetNcnn_append(JNIEnv *env, jobject thiz, jobject image) {
    void *pixels= nullptr;
    AndroidBitmapInfo info;
    int ret = AndroidBitmap_getInfo(env, image, &info);
    if (ret != 0) {
        return false;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return false;
    }

    ret = AndroidBitmap_lockPixels(env, image, &pixels);
    if (ret != 0) {
        return false;
    }

    cv::Mat mat(info.height, info.width, CV_8UC4, pixels);
    cv::Mat rgb(info.height, info.width, CV_8UC3);
    cv::cvtColor(mat, rgb, cv::COLOR_BGRA2RGB);
    {
        ncnn::MutexLockGuard g(lock);
        if (g_input.size() > 20) {
            g_input.clear();
        }
        g_input.emplace_back(rgb);
    }

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "append image");


    AndroidBitmap_unlockPixels(env, image);
    return true;
}

JNIEXPORT jfloatArray JNICALL
Java_com_p4f_esp32camai_NanoDetNcnn_fetch(JNIEnv *env, jobject thiz) {
    bool empty = false;
    {
        ncnn::MutexLockGuard g(lock);
        empty = g_output.empty();
    }
    if (empty) {
        // return empty
        return env->NewFloatArray(0);
    }

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "fetch result");

    std::vector<Object> out;
    {
        ncnn::MutexLockGuard g(lock);
        out = g_output[g_output.size() - 1];
        g_output.clear();
    }

    jfloatArray jarr = env->NewFloatArray(6 * out.size());
    jfloat* parr = env->GetFloatArrayElements(jarr, NULL);
    int offset = 0;
    for (int i = 0; i < out.size(); ++i, offset += 6) {
        Object item = out[i];
        parr[offset] = item.rect.x;
        parr[offset + 1] = item.rect.y;
        parr[offset + 2] = item.rect.width;
        parr[offset + 3] = item.rect.height;
        parr[offset + 4] = item.label;
        parr[offset + 5] = item.prob;
    }
    env->ReleaseFloatArrayElements(jarr, parr, 0);
    return jarr;
}

}
