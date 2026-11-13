#pragma once

#include <atomic>
#include <cstdint>
#include <mutex>

namespace aimbot {

class Fullbright {
public:
  static Fullbright &instance();

  bool initialize();
  void setEnabled(bool enabled);
  bool isEnabled() const;

private:
  Fullbright() = default;

  std::mutex m_mutex;
  uintptr_t m_target = 0;
  bool m_initialized = false;
  std::atomic<bool> m_enabled{false};
};

} // namespace aimbot
