#pragma once

#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <mutex>
#include <sys/types.h>
#include <sys/uio.h>
#include <type_traits>
#include <unistd.h>

#include <dlfcn.h>

namespace aimbot::mem {

inline bool isPlausiblePointer(uintptr_t address) {
  return address >= 0x1000 && (address & 0x3) == 0;
}

// /proc/self/mem is a fallback for kernels that block process_vm_readv.
inline int procMemFd() {
  static std::once_flag once;
  static int fd = -1;
  std::call_once(once, [] {
    fd = open("/proc/self/mem", O_RDWR | O_CLOEXEC);
  });
  return fd;
}

inline bool read(uintptr_t address, void *out, size_t size) {
  if (!out || size == 0 || !isPlausiblePointer(address)) return false;

  iovec local{out, size};
  iovec remote{reinterpret_cast<void *>(address), size};
  const ssize_t copied =
      process_vm_readv(getpid(), &local, 1, &remote, 1, 0);
  if (copied == static_cast<ssize_t>(size)) return true;

  const int fd = procMemFd();
  if (fd < 0) return false;
  const ssize_t fallback =
      pread(fd, out, size, static_cast<off_t>(address));
  return fallback == static_cast<ssize_t>(size);
}

inline bool write(uintptr_t address, const void *data, size_t size) {
  if (!data || size == 0 || !isPlausiblePointer(address)) return false;

  iovec local{const_cast<void *>(data), size};
  iovec remote{reinterpret_cast<void *>(address), size};
  const ssize_t copied =
      process_vm_writev(getpid(), &local, 1, &remote, 1, 0);
  if (copied == static_cast<ssize_t>(size)) return true;

  const int fd = procMemFd();
  if (fd < 0) return false;
  const ssize_t fallback =
      pwrite(fd, data, size, static_cast<off_t>(address));
  return fallback == static_cast<ssize_t>(size);
}

template <typename T>
inline bool read(uintptr_t address, T &out) {
  static_assert(std::is_trivially_copyable<T>::value,
                "mem::read requires a trivial type");
  return read(address, &out, sizeof(T));
}

template <typename T>
inline bool write(uintptr_t address, const T &value) {
  static_assert(std::is_trivially_copyable<T>::value,
                "mem::write requires a trivial type");
  return write(address, &value, sizeof(T));
}

inline uintptr_t gameBase(const void *address) {
  if (!address) return 0;
  Dl_info info{};
  if (dladdr(address, &info) == 0 || !info.dli_fname) return 0;
  if (!std::strstr(info.dli_fname, "libminecraftpe.so")) return 0;
  return reinterpret_cast<uintptr_t>(info.dli_fbase);
}

// Cached [start,end) range of libminecraftpe.so from /proc/self/maps. Reading
// the range once turns the per-actor vtable check into a couple of integer
// compares instead of a dladdr() call (dladdr on a 300+ MB lib was a real
// per-frame cost when validating every nearby actor).
inline bool gameRange(uintptr_t &start, uintptr_t &end) {
  static std::once_flag once;
  static uintptr_t rangeStart = 0;
  static uintptr_t rangeEnd = 0;
  std::call_once(once, [] {
    FILE *maps = std::fopen("/proc/self/maps", "r");
    if (!maps) return;
    char line[512];
    while (std::fgets(line, sizeof(line), maps)) {
      if (!std::strstr(line, "libminecraftpe.so")) continue;
      uintptr_t a = 0;
      uintptr_t b = 0;
      if (std::sscanf(line, "%zx-%zx", &a, &b) != 2) continue;
      if (rangeStart == 0 || a < rangeStart) rangeStart = a;
      if (b > rangeEnd) rangeEnd = b;
    }
    std::fclose(maps);
  });
  start = rangeStart;
  end = rangeEnd;
  return rangeEnd > rangeStart;
}

inline bool isGameAddress(const void *address) {
  if (!address) return false;
  const auto value = reinterpret_cast<uintptr_t>(address);
  uintptr_t start = 0;
  uintptr_t end = 0;
  if (gameRange(start, end)) {
    return value >= start && value < end;
  }
  Dl_info info{};
  if (dladdr(address, &info) == 0 || !info.dli_fname) return false;
  return std::strstr(info.dli_fname, "libminecraftpe.so") != nullptr;
}

} // namespace aimbot::mem
