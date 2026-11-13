#pragma once

#include <android/log.h>

#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <mutex>

// Пишет строку и в logcat, и в файл (если путь задан из Java).
// Файл нужен потому, что logcat из Termux/ADB на Android 13 показывает
// только логи своего uid, а логи игры — нет.
namespace aimbot {

inline std::mutex &diagMutex() {
  static std::mutex mutex;
  return mutex;
}

inline char *diagPathBuf() {
  static char path[512] = {0};
  return path;
}

inline bool diagLogActive() {
  std::lock_guard<std::mutex> lock(diagMutex());
  return diagPathBuf()[0] != '\0';
}

inline void setDiagLogPath(const char *path) {
  std::lock_guard<std::mutex> lock(diagMutex());
  char *buf = diagPathBuf();
  if (!path || !*path) {
    buf[0] = '\0';
    return;
  }
  std::snprintf(buf, sizeof(char) * 512, "%s", path);
  // Начинаем новый файл с чистого листа.
  FILE *file = std::fopen(buf, "w");
  if (file) {
    std::fclose(file);
  }
}

inline void diagLog(const char *tag, const char *fmt, ...) {
  char line[1024];
  va_list args;
  va_start(args, fmt);
  std::vsnprintf(line, sizeof(line), fmt, args);
  va_end(args);

  __android_log_print(ANDROID_LOG_INFO, tag, "%s", line);

  std::lock_guard<std::mutex> lock(diagMutex());
  const char *path = diagPathBuf();
  if (!path[0]) {
    return;
  }
  FILE *file = std::fopen(path, "a");
  if (!file) {
    return;
  }
  std::fprintf(file, "%s\n", line);
  std::fclose(file);
}

} // namespace aimbot
