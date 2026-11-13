#pragma once

#include <cstdint>
#include <string>

namespace gameecs {

// Actor entity context layout (Bedrock 1.26.x):
//   Actor + 0x08: EntityContext
//     + 0x00: EntityRegistry* (unused)
//     + 0x08: entt::basic_registry<EntityId>*
//     + 0x10: EntityId (uint32, version in high bits)
constexpr uintptr_t kActorEntityContext = 0x08;
constexpr uintptr_t kEntityContextRegistry = 0x08;
constexpr uintptr_t kEntityContextEntity = 0x10;

bool registryForActor(void *actor, void **registry, uint32_t *entity);

bool hasOnGround(void *actor);

// Наличие произвольного компонента по entt-хешу (fnv1a32 от имени типа).
bool hasComponent(void *actor, unsigned int hash);
// Игрок в воде (WasInWaterFlagComponent / WaterTravelFlagComponent).
bool inWater(void *actor);

struct MoveInputState {
  bool sneaking = false;
  bool sprinting = false;
  bool jumping = false;
};

bool readMoveInput(void *actor, MoveInputState &out);
bool setMoveSprinting(void *actor, bool value);
bool setMoveJumping(void *actor, bool value);

// Указатель на ItemStack из контейнера экипировки. containerOffset — смещение
// указателя на контейнер внутри ActorEquipmentComponent (0x00 — рука,
// 0x10 — альтернативный контейнер в некоторых сборках). slot — индекс слота.
void *equipmentItemStack(void *actor, unsigned long containerOffset,
                         int slot);

bool hasArmor(void *actor);
int armorPieceCount(void *actor);
std::uint32_t armorMaterialMask(void *actor);
float readHealth(void *actor);
std::string offhandItemName(void *actor);
bool isInvisible(void *actor);
bool setSprintingFlag(void *actor, bool value);

} // namespace gameecs
