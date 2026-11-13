#include "AntiBot.hpp"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <cmath>
#include <string>
#include <unordered_map>

#include "Esp.hpp"
#include "GameEcs.hpp"

namespace aimbot {
namespace {

int64_t nowMs() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

bool isNameByte(unsigned char c) {
  return c >= 0x80 || std::isalnum(c) != 0;
}

std::string normalizedName(const std::string &name) {
  std::string out;
  out.reserve(name.size());
  bool lastSeparator = false;
  for (unsigned char c : name) {
    if (c >= 'A' && c <= 'Z') c = static_cast<unsigned char>(c + 32);
    if (isNameByte(c)) {
      out.push_back(static_cast<char>(c));
      lastSeparator = false;
    } else if (!out.empty() && !lastSeparator) {
      out.push_back('_');
      lastSeparator = true;
    }
  }
  while (!out.empty() && out.back() == '_') out.pop_back();
  return out;
}

bool containsToken(const std::string &name, const char *token) {
  const std::string needle(token);
  if (needle.empty() || needle.size() > name.size()) return false;
  size_t pos = 0;
  while ((pos = name.find(needle, pos)) != std::string::npos) {
    const bool leftOk =
        pos == 0 || !isNameByte(static_cast<unsigned char>(name[pos - 1]));
    const size_t end = pos + needle.size();
    const bool rightOk =
        end >= name.size() ||
        !isNameByte(static_cast<unsigned char>(name[end]));
    if (leftOk && rightOk) return true;
    ++pos;
  }
  return false;
}

bool suspiciousName(const std::string &name) {
  if (name.empty()) return false;

  static const char *const kTokens[] = {
      "bot",      "npc",      "dummy",     "guard",     "watchdog",
      "sentinel", "fakeplayer", "testbot", "antibot",   "aimbot",
      "npcbot",   "polarbot", "matrixbot", "grimbot",   "acbot",
      "citizen",
  };
  for (const char *token : kTokens) {
    if (containsToken(name, token)) return true;
  }

  const size_t bot = name.find("bot");
  if (bot != std::string::npos) {
    const size_t end = bot + 3;
    bool digitsAfter = end < name.size();
    for (size_t i = end; i < name.size() && digitsAfter; ++i) {
      if (!std::isdigit(static_cast<unsigned char>(name[i]))) digitsAfter = false;
    }
    bool digitsBefore = true;
    for (size_t i = 0; i < bot && digitsBefore; ++i) {
      if (!std::isdigit(static_cast<unsigned char>(name[i]))) digitsBefore = false;
    }
    if (digitsAfter || digitsBefore) return true;
  }

  return false;
}

float distanceSq(const Vec3 &a, const Vec3 &b) {
  const float dx = a.x - b.x;
  const float dy = a.y - b.y;
  const float dz = a.z - b.z;
  return dx * dx + dy * dy + dz * dz;
}

} // namespace

AntiBot &AntiBot::instance() {
  static AntiBot instance;
  return instance;
}

void AntiBot::setEnabled(bool enabled) {
  mEnabled.store(enabled, std::memory_order_release);
  if (!enabled) reset();
}

bool AntiBot::isEnabled() const {
  return mEnabled.load(std::memory_order_acquire);
}

void AntiBot::reset() {
  std::lock_guard<std::mutex> lock(mMutex);
  mRecords.clear();
}

void AntiBot::update(const std::vector<void *> &actors, void *localPlayer) {
  if (!mEnabled.load(std::memory_order_acquire)) return;

  const int64_t now = nowMs();
  ClientBridge &bridge = ClientBridge::instance();

  struct Observation {
    void *actor = nullptr;
    Vec3 pos{};
    std::string name;
    bool hasPos = false;
    bool noArmor = false;
  };
  std::vector<Observation> observations;
  observations.reserve(actors.size());

  std::unordered_map<std::string, int> nameCounts;
  int emptyNames = 0;
  for (void *actor : actors) {
    if (!actor || actor == localPlayer) continue;
    if (!bridge.isPlayer(actor)) continue;

    Observation observation;
    observation.actor = actor;
    observation.hasPos = bridge.getPosition(actor, observation.pos);
    observation.name = normalizedName(Esp::instance().getActorName(actor));
    observation.noArmor = gameecs::armorPieceCount(actor) <= 0;
    if (observation.name.empty()) {
      ++emptyNames;
    } else {
      ++nameCounts[observation.name];
    }
    observations.push_back(std::move(observation));
  }
  const bool emptyNamesReliable =
      observations.size() < 2 ||
      emptyNames * 2 <= static_cast<int>(observations.size());

  std::lock_guard<std::mutex> lock(mMutex);
  for (auto it = mRecords.begin(); it != mRecords.end();) {
    if (now - it->second.lastSeenMs > 10000) {
      it = mRecords.erase(it);
    } else {
      ++it;
    }
  }
  if (mRecords.size() > 2048) mRecords.clear();

  for (const Observation &observation : observations) {
    Record &record = mRecords[observation.actor];
    if (record.firstSeenMs == 0) {
      record.firstSeenMs = now;
      record.lastSampleMs = now;
      if (observation.hasPos) record.lastPos = observation.pos;
    }
    record.lastSeenMs = now;

    if (observation.hasPos && now - record.lastSampleMs >= 180) {
      if (record.samples > 0) record.moved += std::sqrt(
          distanceSq(record.lastPos, observation.pos));
      record.lastPos = observation.pos;
      record.lastSampleMs = now;
      ++record.samples;
    }

    const bool emptyName = observation.name.empty();
    const bool duplicate =
        !emptyName && nameCounts[observation.name] > 1;
    const bool still = record.samples >= 5 && record.moved < 0.08f;
    const int64_t aliveFor = now - record.firstSeenMs;

    record.bot = suspiciousName(observation.name) ||
                 (emptyNamesReliable && emptyName && observation.noArmor &&
                  aliveFor >= 1000) ||
                 (emptyNamesReliable && emptyName && still &&
                  aliveFor >= 2000) ||
                 (duplicate && observation.noArmor);
  }
}

bool AntiBot::isBot(void *actor) const {
  if (!mEnabled.load(std::memory_order_acquire) || !actor) return false;
  std::lock_guard<std::mutex> lock(mMutex);
  const auto it = mRecords.find(actor);
  return it != mRecords.end() && it->second.bot;
}

} // namespace aimbot
