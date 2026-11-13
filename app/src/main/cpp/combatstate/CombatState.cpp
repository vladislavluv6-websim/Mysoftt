#include "CombatState.hpp"

#include <atomic>
#include <chrono>

namespace combatstate {
namespace {

std::atomic<std::int64_t> g_suppressUntilMs{0};

std::int64_t steadyMs() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

} // namespace

void suppressSprint(std::uint32_t ms) {
  if (ms == 0) {
    clearSprintSuppress();
    return;
  }
  g_suppressUntilMs.store(steadyMs() + static_cast<std::int64_t>(ms),
                          std::memory_order_release);
}

void clearSprintSuppress() {
  g_suppressUntilMs.store(0, std::memory_order_release);
}

bool sprintSuppressed() {
  const std::int64_t until = g_suppressUntilMs.load(std::memory_order_acquire);
  return until != 0 && steadyMs() < until;
}

} // namespace combatstate
