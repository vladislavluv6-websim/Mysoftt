#include "Fullbright.hpp"

#include <android/log.h>

#include <array>
#include <cstring>
#include <span>
#include <vector>

#include "Memory.hpp"
#include "Offsets.hpp"
#include "pl/memory/Patch.hpp"
#include "pl/memory/Signature.hpp"

namespace aimbot {
namespace {

constexpr const char *kTag = "AimAssistFullbright";
constexpr const char *kPatchName = "aimassist.fullbright";

std::array<uint8_t, 12> patchBytes() {
  return {0x40, 0x8F, 0xA8, 0x52, 0x00, 0x00,
          0x27, 0x1E, 0xC0, 0x03, 0x5F, 0xD6};
}

} // namespace

Fullbright &Fullbright::instance() {
  static Fullbright fullbright;
  return fullbright;
}

bool Fullbright::initialize() {
  std::lock_guard<std::mutex> lock(m_mutex);
  if (m_initialized) return true;

  const uintptr_t target = pl::memory::resolveSignature(
      offsets::kFullbrightSignature, offsets::kMinecraftLibrary);
  if (!target || !mem::isGameAddress(reinterpret_cast<const void *>(target))) {
    __android_log_print(ANDROID_LOG_WARN, kTag,
                        "Fullbright signature not found");
    return false;
  }

  const std::vector<uint8_t> current =
      pl::memory::readBytes(target, patchBytes().size());
  if (current.size() != patchBytes().size()) {
    __android_log_print(ANDROID_LOG_WARN, kTag,
                        "Fullbright target unreadable at %p",
                        reinterpret_cast<void *>(target));
    return false;
  }
  const auto patch = patchBytes();
  if (std::memcmp(current.data(), patch.data(), patch.size()) == 0) {
    // Патч уже стоит (повторная инициализация после смены цели).
    m_target = target;
    m_initialized = true;
    return true;
  }

  m_target = target;
  m_initialized = true;
  __android_log_print(ANDROID_LOG_INFO, kTag, "Fullbright ready at %p",
                      reinterpret_cast<void *>(target));
  return true;
}

void Fullbright::setEnabled(bool enabled) {
  if (enabled && !m_initialized) initialize();
  std::lock_guard<std::mutex> lock(m_mutex);

  if (enabled == m_enabled.load(std::memory_order_relaxed)) return;
  if (!m_target) return;

  if (enabled) {
    const auto patch = patchBytes();
    m_enabled.store(pl::memory::writeBytes(
                        m_target,
                        std::span<const uint8_t>(patch.data(), patch.size()),
                        kPatchName),
                    std::memory_order_relaxed);
  } else {
    pl::memory::revertPatch(kPatchName);
    m_enabled.store(false, std::memory_order_relaxed);
  }
  __android_log_print(ANDROID_LOG_INFO, kTag, "Fullbright=%d",
                      m_enabled.load(std::memory_order_relaxed) ? 1 : 0);
}

bool Fullbright::isEnabled() const {
  return m_enabled.load(std::memory_order_relaxed);
}

} // namespace aimbot
