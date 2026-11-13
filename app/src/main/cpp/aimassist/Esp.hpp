#pragma once

#include <atomic>
#include <chrono>
#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "ClientBridge.hpp"

namespace aimbot {

struct NameTagLabel {
  float x = 0.0f;
  float y = 0.0f;
  float distance = 0.0f;
  int armorPieces = 0;
  uint32_t armorMask = 0;
  float health = 20.0f;
  bool friendPlayer = false;
  std::string offhandItem;
  std::string name;
};

struct EspBoxLabel {
  float x = 0.0f;
  float y = 0.0f;
  float width = 0.0f;
  float height = 0.0f;
  uint32_t color = 0xFFFFFFFFu;
  int armorPieces = 0;
};

struct TargetEspState {
  bool visible = false;
  float x = 0.0f;
  float y = 0.0f;
  float width = 0.0f;
  float height = 0.0f;
  int64_t ageMs = 0;
  float yaw = 0.0f;
  // Camera + basis + target AABB so Java can project 3D points to screen.
  float camX = 0.0f;
  float camY = 0.0f;
  float camZ = 0.0f;
  float minX = 0.0f;
  float minY = 0.0f;
  float minZ = 0.0f;
  float maxX = 0.0f;
  float maxY = 0.0f;
  float maxZ = 0.0f;
  float fx = 0.0f;
  float fy = 0.0f;
  float fz = 0.0f;
  float rx = 0.0f;
  float rz = 0.0f;
  float ux = 0.0f;
  float uy = 0.0f;
  float uz = 0.0f;
  float focal = 0.0f;
  float screenW = 0.0f;
  float screenH = 0.0f;
};

class Esp {
public:
  static Esp &instance();

  bool initialize();
  void shutdown();

  void setEnabled(bool enabled);
  bool isEnabled() const;

  void setPlayers(bool enabled);
  void setMobs(bool enabled);
  void setRange(float range);
  void setPlayerColor(uint32_t argb);
  void setMobColor(uint32_t argb);
  void setArmoredColor(uint32_t argb);
  void setArmorColors(bool enabled);
  void setEspBoxStyle(int style);
  int getEspBoxStyle() const;
  std::vector<EspBoxLabel> copyEspBoxes() const;

  // Name Tags: экранные координаты считает native (game thread), Java
  // overlay только рисует готовые подписи.
  void setNameTags(bool enabled);
  bool isNameTagsEnabled() const;
  void setNameTagSurface(float width, float height);
  std::vector<NameTagLabel> copyNameTags() const;

  // Target ESP: виден только после удара TriggerBot по цели. Native
  // считает экранный прямоугольник цели, Java рисует модель.
  void setTargetEsp(bool enabled);
  bool isTargetEspEnabled() const;
  void setTargetEspHoldMs(int holdMs);
  void markTargetEspHit(void *actor);
  TargetEspState copyTargetEsp() const;

  void setFriendNames(const std::string &names);
  bool isFriend(void *actor);
  std::string getActorName(void *actor);

  // Called from the LevelRenderer::renderLevel detour.
  void render(void *levelRenderer, void *screenContext);

private:
  Esp() = default;

  void updateNameTags(void *player, uintptr_t playerRenderer,
                      const float camera[3]);
  void updateEspBoxes(void *player, uintptr_t playerRenderer,
                      const float camera[3]);
  void updateTargetEsp(void *player, uintptr_t playerRenderer,
                       const float camera[3]);
  std::string actorName(void *actor, std::chrono::steady_clock::time_point now);
  uint32_t armorMaskFor(void *actor, std::chrono::steady_clock::time_point now);
  std::string offhandFor(void *actor, std::chrono::steady_clock::time_point now);

  ClientBridge &m_bridge = ClientBridge::instance();
  std::mutex m_initMutex;
  std::atomic<bool> m_initialized{false};
  std::atomic<bool> m_enabled{false};
  std::atomic<bool> m_showPlayers{true};
  std::atomic<bool> m_showMobs{true};
  std::atomic<float> m_range{32.0f};
  std::atomic<uint32_t> m_playerColor{0xFFFF5555u};
  std::atomic<uint32_t> m_mobColor{0xFFFFAA00u};
  std::atomic<uint32_t> m_armoredColor{0xFF4E83FFu};
  std::atomic<bool> m_armorColors{true};
  std::atomic<int> m_espBoxStyle{0};
  std::atomic<bool> m_nameTags{true};
  std::atomic<float> m_surfaceWidth{0.0f};
  std::atomic<float> m_surfaceHeight{0.0f};
  std::vector<void *> m_actors;
  std::chrono::steady_clock::time_point m_lastLog{};
  std::chrono::steady_clock::time_point m_lastScan{};
  std::chrono::steady_clock::time_point m_lastBoxCompute{};
  mutable std::mutex m_nameMutex;
  mutable std::mutex m_friendMutex;
  std::unordered_set<std::string> m_friends;
  std::vector<NameTagLabel> m_nameSnapshot;
  mutable std::mutex m_boxMutex;
  std::vector<EspBoxLabel> m_boxSnapshot;
  std::atomic<bool> m_targetEsp{false};
  std::atomic<int> m_targetEspHoldMs{650};
  mutable std::mutex m_targetMutex;
  void *m_targetEspActor = nullptr;
  std::chrono::steady_clock::time_point m_targetEspHit{};
  TargetEspState m_targetEspSnapshot;
  std::unordered_map<void *, std::pair<std::string,
                                       std::chrono::steady_clock::time_point>>
      m_nameCache;
  mutable std::mutex m_metaMutex;
  std::unordered_map<void *, std::pair<uint32_t,
                                       std::chrono::steady_clock::time_point>>
      m_armorCache;
  std::unordered_map<void *, std::pair<std::string,
                                       std::chrono::steady_clock::time_point>>
      m_offhandCache;
};

} // namespace aimbot
