#pragma once

#include <atomic>
#include <cstdint>
#include <mutex>
#include <unordered_map>
#include <vector>

#include "ClientBridge.hpp"

namespace aimbot {

class AntiBot {
public:
  static AntiBot &instance();

  void setEnabled(bool enabled);
  bool isEnabled() const;

  void update(const std::vector<void *> &actors, void *localPlayer);
  bool isBot(void *actor) const;
  void reset();

private:
  AntiBot() = default;

  struct Record {
    int64_t firstSeenMs = 0;
    int64_t lastSeenMs = 0;
    int64_t lastSampleMs = 0;
    Vec3 lastPos{};
    float moved = 0.0f;
    int samples = 0;
    bool bot = false;
  };

  mutable std::mutex mMutex;
  std::unordered_map<void *, Record> mRecords;
  std::atomic<bool> mEnabled{true};
};

} // namespace aimbot
