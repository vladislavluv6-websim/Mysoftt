#pragma once

#include <cstdint>
#include <unordered_map>
#include <vector>

namespace aimbot {

struct Vec2 {
  float pitch = 0.0f;
  float yaw = 0.0f;
};

struct Vec3 {
  float x = 0.0f;
  float y = 0.0f;
  float z = 0.0f;
};

struct ViewAngles {
  float pitch = 0.0f;
  float yaw = 0.0f;
};

struct Aabb {
  Vec3 min{};
  Vec3 max{};
};

// Тонкий слой над SDK клиента.
// Всё, что зависит от конкретного клиента, находится здесь.
// Если у тебя есть готовые классы ClientInstance/LocalPlayer/Level,
// замени тела методов на их вызовы — AimAssist.cpp менять не придётся.
class ClientBridge {
public:
  using MovePacketSender = void (*)(void *player, float yaw, float pitch);

  static ClientBridge &instance();

  bool initialize();
  void shutdown();

  // ClientInstance приходит из хука ClientInstance::update.
  void setClientInstance(void *client);
  void *getClientInstance() const;

  // 0 = ванильная анимация; 1..5 = кривые прогресса взмаха.
  void setSwingStyle(int style);
  void setSwingSpeed(int percent);
  void setSwingAmplitude(int percent);
  void armSwing();

  void *getLocalPlayer() const;

  // Текущий view из ActorRotationComponent: {pitch, yaw}.
  bool getViewAngles(void *player, ViewAngles &out) const;

  // Двигает локальную камеру игровым методом applyTurnDelta.
  // pitchUp < 0 — вверх, yaw > 0 — вправо (как в 1.26.60).
  bool applyTurnDelta(void *player, float pitchUp, float yaw);

  // Синхронизирует body yaw, сохраняя текущий pitch компонента.
  bool setBodyYaw(void *player, float yaw);

  bool getPosition(void *actor, Vec3 &out) const;
  bool getActorAabb(void *actor, Aabb &out) const;

  // Хитбокс в мировых координатах. Если AABB не читается или не похож на
  // правду — строит стандартный бокс от позиции (у игрока позиция = глаза,
  // у моба = ноги).
  bool getActorBounds(void *actor, Aabb &out) const;

  // Позиция глаз локального игрока: низ хитбокса + рост глаз.
  bool getEyePosition(void *player, Vec3 &out) const;
  uint32_t getActorCategories(void *actor) const;
  bool isPlayer(void *actor) const;
  int getActorType(void *actor) const;
  uint64_t getActorFlags(void *actor) const;

  // Заполняет vector без per-frame аллокаций: capacity сохраняется между кадрами.
  void fillNearbyActors(void *player, float range, std::vector<void *> &out) const;

  // GameMode текущего ClientInstance (SurvivalMode). Валидируется обратной
  // ссылкой на клиент и адресом attack-слота в libminecraftpe.
  void *getGameMode() const;
  // Реальная атака игровым методом GameMode::attack(Actor&, Vec3 const&).
  // Вызывать только с игрового потока (ClientInstance::update).
  bool attackActor(void *actor, const Vec3 &hitPoint);

private:
  // Читает один набор актёров и добавляет валидные указатели в out без
  // дублей. actorType — фильтр, который принимает игровой fetchNearbyActors.
  void appendNearbyActors(void *player, const Vec3 &extent, int actorType,
                          std::vector<void *> &out) const;

public:

  // Line-of-sight. Если сигнатура не найдена — возвращает true,
  // чтобы visibleCheck не блокировал наведение.
  bool hasLineOfSight(void *player, const Vec3 &from, const Vec3 &to) const;

  // Явная отправка MovePlayerPacket. По умолчанию nullptr:
  // в 1.26.x локальные углы сами уходят в PlayerAuthInput.
  void setMovePacketSender(MovePacketSender sender);
  // Silent aim: inject the given rotation into the outgoing PlayerAuthInput
  // without moving the local camera. active=false restores normal rotation.
  void setSilentAim(bool active, float pitch, float yaw);
  void sendMovePacket(void *player, float yaw, float pitch) const;

private:
  ClientBridge() = default;

  mutable std::mutex m_playerCacheMutex;
  mutable std::unordered_map<void *, std::pair<bool,
      std::chrono::steady_clock::time_point>> m_playerCache;
};

} // namespace aimbot
