#include "GameEcs.hpp"

#include <algorithm>
#include <cerrno>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <string>
#include <sys/uio.h>
#include <unistd.h>

#include <entt/entt.hpp>

namespace gameecs {
namespace {

enum class EntityId : std::uint32_t {};

} // namespace
} // namespace gameecs

template <> struct entt::internal::entt_traits<gameecs::EntityId> {
  using value_type = gameecs::EntityId;
  using entity_type = std::uint32_t;
  using version_type = std::uint16_t;
  static constexpr entity_type entity_mask = 0x3FFFF;
  static constexpr entity_type version_mask = 0x3FFF;
};

namespace gameecs {
namespace {

using Registry = entt::basic_registry<EntityId>;
using SparseSet = Registry::common_type;

// entt component type hashes in the running Bedrock build.
constexpr entt::id_type kHashMoveInput = 0x018B1887;
constexpr entt::id_type kHashOnGround = 0xC29078A0;
constexpr entt::id_type kHashEquipment = 0xB06141A9;
constexpr entt::id_type kHashActorData = 0xC67426F3;
constexpr entt::id_type kHashAttributes = 0xB6B29444;

constexpr uint8_t kMoveFlagSneakDown = 0;
constexpr uint8_t kMoveFlagJumpDown = 7;
constexpr uint8_t kMoveFlagSprintDown = 8;

constexpr uint8_t kMoveStateSprinting = 1;
constexpr uint8_t kMoveStateJumping = 4;

constexpr uintptr_t kEquipmentOffhandContainer = 0x00;
constexpr uintptr_t kEquipmentArmorContainer = 0x08;
constexpr uintptr_t kContainerItems = 0x140;
constexpr uintptr_t kStackSize = 0x98;
constexpr uintptr_t kStackCount = 0x22;
constexpr uintptr_t kAttributesInstances = 0x18;
constexpr uintptr_t kAttributeInstanceSize = 0x88;
constexpr uintptr_t kAttributeCurrentMax = 0x78;
constexpr uintptr_t kAttributeCurrentValue = 0x7C;
constexpr size_t kHealthAttribute = 5;
constexpr size_t kOffhandSlot = 0;

bool readable(const void *address, size_t size) {
  if (!address || reinterpret_cast<uintptr_t>(address) < 0x1000) return false;
  iovec local{const_cast<void *>(address), size};
  iovec remote{const_cast<void *>(address), size};
  return process_vm_readv(getpid(), &local, 1, &remote, 1, 0) ==
         static_cast<ssize_t>(size);
}

bool readValue(uintptr_t address, void *out, size_t size) {
  if (!out || address < 0x1000) return false;
  iovec local{out, size};
  iovec remote{reinterpret_cast<void *>(address), size};
  return process_vm_readv(getpid(), &local, 1, &remote, 1, 0) ==
         static_cast<ssize_t>(size);
}

template <typename T> bool readValue(uintptr_t address, T &out) {
  return readValue(address, &out, sizeof(T));
}

SparseSet *poolFor(void *actor, entt::id_type hash, EntityId &entity) {
  void *registry = nullptr;
  uint32_t raw = 0;
  if (!registryForActor(actor, &registry, &raw)) return nullptr;
  if (!readable(registry, sizeof(void *) * 4)) return nullptr;
  auto *reg = static_cast<Registry *>(registry);
  SparseSet *pool = reg->storage(hash);
  if (!pool) return nullptr;
  entity = static_cast<EntityId>(raw);
  if (!pool->contains(entity)) return nullptr;
  return pool;
}

void *componentFor(void *actor, entt::id_type hash) {
  EntityId entity{};
  SparseSet *pool = poolFor(actor, hash, entity);
  if (!pool) return nullptr;
  return const_cast<void *>(pool->value(entity));
}

bool componentPresent(void *actor, entt::id_type hash) {
  EntityId entity{};
  return poolFor(actor, hash, entity) != nullptr;
}

void updateBitset32(void *component, uint8_t bit, bool value) {
  if (!component) return;
  const auto address = reinterpret_cast<uintptr_t>(component);
  uint32_t flags = 0;
  if (!readValue(address, flags)) return;
  const uint32_t mask = 1u << bit;
  flags = value ? (flags | mask) : (flags & ~mask);
  iovec local{&flags, sizeof(flags)};
  iovec remote{reinterpret_cast<void *>(address), sizeof(flags)};
  process_vm_writev(getpid(), &local, 1, &remote, 1, 0);
}

void updateBitset16(void *component, uintptr_t offset, uint8_t bit,
                    bool value) {
  if (!component) return;
  const auto address = reinterpret_cast<uintptr_t>(component) + offset;
  uint16_t flags = 0;
  if (!readValue(address, flags)) return;
  const uint16_t mask = static_cast<uint16_t>(1u << bit);
  flags = value ? static_cast<uint16_t>(flags | mask)
                : static_cast<uint16_t>(flags & ~mask);
  iovec local{&flags, sizeof(flags)};
  iovec remote{reinterpret_cast<void *>(address), sizeof(flags)};
  process_vm_writev(getpid(), &local, 1, &remote, 1, 0);
}

bool readBit32(void *component, uint8_t bit) {
  if (!component) return false;
  uint32_t flags = 0;
  if (!readValue(reinterpret_cast<uintptr_t>(component), flags)) return false;
  return (flags & (1u << bit)) != 0;
}

bool readBit64(void *component, uint8_t bit) {
  if (!component) return false;
  uint64_t flags = 0;
  if (!readValue(reinterpret_cast<uintptr_t>(component), flags)) return false;
  return (flags & (1ull << bit)) != 0;
}

} // namespace

bool registryForActor(void *actor, void **registry, uint32_t *entity) {
  if (!actor || !registry || !entity) return false;
  const auto base = reinterpret_cast<uintptr_t>(actor);
  uintptr_t contextRegistry = 0;
  uint32_t raw = 0;
  if (!readValue(base + kActorEntityContext + kEntityContextRegistry,
                 contextRegistry) ||
      !readValue(base + kActorEntityContext + kEntityContextEntity, raw)) {
    return false;
  }
  if (contextRegistry < 0x10000 || (contextRegistry & 0x7) != 0) return false;
  *registry = reinterpret_cast<void *>(contextRegistry);
  *entity = raw;
  return true;
}

bool hasOnGround(void *actor) {
  // OnGround is an empty tag component: presence is the value.
  return componentPresent(actor, kHashOnGround);
}

bool readMoveInput(void *actor, MoveInputState &out) {
  void *component = componentFor(actor, kHashMoveInput);
  if (!component) return false;
  out.sneaking = readBit32(component, kMoveFlagSneakDown);
  out.sprinting = readBit32(component, kMoveFlagSprintDown);
  out.jumping = readBit32(component, kMoveFlagJumpDown);
  return true;
}

bool setMoveSprinting(void *actor, bool value) {
  void *component = componentFor(actor, kHashMoveInput);
  if (!component) return false;
  updateBitset32(component, kMoveFlagSprintDown, value);
  updateBitset32(reinterpret_cast<void *>(
                     reinterpret_cast<uintptr_t>(component) + 0x10),
                 kMoveFlagSprintDown, value);
  updateBitset16(component, 0x60, kMoveStateSprinting, value);
  return true;
}

bool setMoveJumping(void *actor, bool value) {
  void *component = componentFor(actor, kHashMoveInput);
  if (!component) return false;
  updateBitset32(component, kMoveFlagJumpDown, value);
  updateBitset32(reinterpret_cast<void *>(
                     reinterpret_cast<uintptr_t>(component) + 0x10),
                 kMoveFlagJumpDown, value);
  updateBitset16(component, 0x60, kMoveStateJumping, value);
  return true;
}

namespace {

constexpr uintptr_t kItemName = 0xD8;
constexpr size_t kItemScanBytes = 0x180;

bool readShortString(uintptr_t address, std::string &out) {
  uint8_t first = 0;
  if (!readValue(address, first)) return false;
  uintptr_t data = 0;
  size_t size = 0;
  if ((first & 1u) != 0) {
    uint64_t value = 0;
    uintptr_t pointer = 0;
    if (!readValue(address + 8, value) ||
        !readValue(address + 16, pointer)) {
      return false;
    }
    if (value == 0 || value > 64 || pointer < 0x1000) return false;
    size = static_cast<size_t>(value);
    data = pointer;
  } else {
    size = static_cast<size_t>(first >> 1);
    if (size == 0 || size > 22) return false;
    data = address + 1;
  }
  char buffer[65]{};
  if (!readValue(data, buffer, size)) return false;
  for (size_t i = 0; i < size; ++i) {
    const unsigned char ch = static_cast<unsigned char>(buffer[i]);
    if (ch < 0x20 || ch > 0x7E) return false;
  }
  out.assign(buffer, size);
  return true;
}

bool isItemNameChar(unsigned char ch) {
  return (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') ||
         ch == '_' || ch == ':' || ch == '/' || ch == '.' || ch == '-';
}

bool plausibleItemName(const std::string &name, bool requireNamespace) {
  if (name.size() < 3 || name.size() > 64) return false;
  const size_t colon = name.find(':');
  if (requireNamespace && colon == std::string::npos) return false;
  if (colon != std::string::npos && colon + 1 >= name.size()) return false;
  bool letter = false;
  for (unsigned char ch : name) {
    if (!isItemNameChar(ch)) return false;
    if (ch >= 'a' && ch <= 'z') letter = true;
  }
  return letter;
}

size_t readPartial(uintptr_t address, uint8_t *out, size_t size) {
  size_t done = 0;
  while (done < size) {
    const size_t chunk = std::min<size_t>(0x40, size - done);
    if (!readValue(address + done, out + done, chunk)) break;
    done += chunk;
  }
  return done;
}

bool shortStringFromBlock(const uint8_t *block, size_t available,
                          size_t offset, std::string &out) {
  if (offset >= available) return false;
  const uint8_t first = block[offset];
  const uint8_t *data = nullptr;
  size_t size = 0;
  if ((first & 1u) != 0) {
    if (offset + 24 > available) return false;
    uint64_t value = 0;
    uintptr_t pointer = 0;
    std::memcpy(&value, block + offset + 8, sizeof(value));
    std::memcpy(&pointer, block + offset + 16, sizeof(pointer));
    if (value == 0 || value > 64 || pointer < 0x1000) return false;
    char buffer[65]{};
    if (!readValue(pointer, buffer, static_cast<size_t>(value))) return false;
    out.assign(buffer, static_cast<size_t>(value));
    return true;
  }
  size = static_cast<size_t>(first >> 1);
  if (size == 0 || size > 22 || offset + 1 + size > available) return false;
  data = block + offset + 1;
  out.assign(reinterpret_cast<const char *>(data), size);
  return true;
}

bool readItemName(uintptr_t item, std::string &out) {
  if (readShortString(item + kItemName, out) &&
      plausibleItemName(out, false)) {
    return true;
  }
  uint8_t block[kItemScanBytes]{};
  const size_t available = readPartial(item, block, sizeof(block));
  for (size_t offset = 0; offset + 24 <= available; offset += 8) {
    if (offset == kItemName) continue;
    std::string candidate;
    if (shortStringFromBlock(block, available, offset, candidate) &&
        plausibleItemName(candidate, true)) {
      out = std::move(candidate);
      return true;
    }
  }
  for (size_t offset = 0; offset + 8 <= available; offset += 8) {
    uintptr_t pointer = 0;
    std::memcpy(&pointer, block + offset, sizeof(pointer));
    if (pointer < 0x1000) continue;
    char text[65]{};
    if (!readValue(pointer, text, sizeof(text) - 1)) continue;
    std::string candidate(text);
    if (plausibleItemName(candidate, true)) {
      out = std::move(candidate);
      return true;
    }
  }
  static const char kNamespace[] = "minecraft:";
  for (size_t offset = 0; offset + sizeof(kNamespace) <= available; ++offset) {
    if (std::memcmp(block + offset, kNamespace, sizeof(kNamespace) - 1) != 0) {
      continue;
    }
    std::string candidate;
    for (size_t i = offset; i < available && candidate.size() < 64; ++i) {
      const unsigned char ch = block[i];
      if (!isItemNameChar(ch)) break;
      candidate.push_back(static_cast<char>(ch));
    }
    if (plausibleItemName(candidate, true)) {
      out = std::move(candidate);
      return true;
    }
  }
  return false;
}

uint8_t armorMaterialFromName(const std::string &name) {
  if (name.empty()) return 0;
  auto has = [&name](const char *needle) {
    return name.find(needle) != std::string::npos;
  };
  if (has("netherite_")) return 6;
  if (has("diamond_")) return 5;
  if (has("golden_") || has("gold_")) return 4;
  if (has("iron_")) return 3;
  if (has("chainmail_")) return 2;
  if (has("leather_")) return 1;
  if (has("copper_")) return 7;
  if (has("turtle_")) return 8;
  return 9;
}

bool readStackItemName(uintptr_t stack, std::string &out) {
  uint8_t count = 0;
  uint8_t valid = 0;
  if (!readValue(stack + kStackCount, count) || count == 0 ||
      !readValue(stack + kStackCount + 1, valid) || valid == 0) {
    return false;
  }
  uintptr_t counter = 0;
  uintptr_t item = 0;
  if (!readValue(stack + 0x8, counter) || counter < 0x1000 ||
      !readValue(counter, item) || item < 0x1000) {
    return false;
  }
  return readItemName(item, out);
}

uint8_t armorMaterialForStack(uintptr_t stack) {
  std::string name;
  if (!readStackItemName(stack, name)) {
    uint8_t count = 0;
    uint8_t valid = 0;
    if (!readValue(stack + kStackCount, count) || count == 0 ||
        !readValue(stack + kStackCount + 1, valid) || valid == 0) {
      return 0;
    }
    return 9;
  }
  return armorMaterialFromName(name);
}

} // namespace


float readHealth(void *actor) {
  void *attributes = componentFor(actor, kHashAttributes);
  if (!attributes) return 20.0f;
  const auto base = reinterpret_cast<uintptr_t>(attributes);
  uintptr_t begin = 0;
  uintptr_t end = 0;
  if (!readValue(base + kAttributesInstances, begin) ||
      !readValue(base + kAttributesInstances + sizeof(void *), end)) {
    return 20.0f;
  }
  if (begin < 0x1000 || end < begin) return 20.0f;
  const size_t bytes = end - begin;
  if (bytes < kAttributeInstanceSize * (kHealthAttribute + 1) ||
      bytes % kAttributeInstanceSize != 0) {
    return 20.0f;
  }
  const uintptr_t instance =
      begin + kHealthAttribute * kAttributeInstanceSize;
  float maxHealth = 0.0f;
  float health = 0.0f;
  if (!readValue(instance + kAttributeCurrentMax, maxHealth) ||
      !readValue(instance + kAttributeCurrentValue, health)) {
    return 20.0f;
  }
  if (!std::isfinite(maxHealth) || !std::isfinite(health) ||
      maxHealth < 1.0f || maxHealth > 2048.0f || health < -0.01f ||
      health > maxHealth + 0.01f) {
    return 20.0f;
  }
  return health < 0.0f ? 0.0f : health;
}

std::string offhandItemName(void *actor) {
  void *equipment = componentFor(actor, kHashEquipment);
  if (!equipment) return {};
  const auto base = reinterpret_cast<uintptr_t>(equipment);
  uintptr_t container = 0;
  if (!readValue(base + kEquipmentOffhandContainer, container) ||
      container < 0x1000) {
    return {};
  }
  uintptr_t begin = 0;
  uintptr_t end = 0;
  if (!readValue(container + kContainerItems, begin) ||
      !readValue(container + kContainerItems + sizeof(void *), end)) {
    return {};
  }
  if (begin < 0x1000 || end < begin) return {};
  const size_t bytes = end - begin;
  if (bytes < kStackSize * (kOffhandSlot + 1) ||
      bytes % kStackSize != 0) {
    return {};
  }
  std::string name;
  if (!readStackItemName(begin + kOffhandSlot * kStackSize, name)) {
    return {};
  }
  return name;
}

std::uint32_t armorMaterialMask(void *actor) {
  void *equipment = componentFor(actor, kHashEquipment);
  if (!equipment) return 0;
  const auto base = reinterpret_cast<uintptr_t>(equipment);
  uintptr_t container = 0;
  if (!readValue(base + kEquipmentArmorContainer, container) ||
      container < 0x1000) {
    return 0;
  }
  uintptr_t begin = 0;
  uintptr_t end = 0;
  if (!readValue(container + kContainerItems, begin) ||
      !readValue(container + kContainerItems + sizeof(void *), end)) {
    return 0;
  }
  if (begin < 0x1000 || end < begin) return 0;
  const size_t bytes = end - begin;
  if (bytes == 0 || bytes % kStackSize != 0) return 0;
  size_t slots = bytes / kStackSize;
  if (slots > 4) slots = 4;
  uint32_t mask = 0;
  for (size_t slot = 0; slot < slots; ++slot) {
    const uintptr_t stack = begin + slot * kStackSize;
    const uint8_t material = armorMaterialForStack(stack);
    if (material != 0) {
      mask |= static_cast<uint32_t>(material) << (slot * 8);
    }
  }
  return mask;
}

int armorPieceCount(void *actor) {
  const uint32_t mask = armorMaterialMask(actor);
  int pieces = 0;
  for (int slot = 0; slot < 4; ++slot) {
    if (((mask >> (slot * 8)) & 0xFFu) != 0) ++pieces;
  }
  return pieces;
}

bool hasArmor(void *actor) { return armorPieceCount(actor) > 0; }

bool isInvisible(void *actor) {
  return readBit64(componentFor(actor, kHashActorData), 5);
}

bool setSprintingFlag(void *actor, bool value) {
  void *data = componentFor(actor, kHashActorData);
  if (!data) return false;
  const auto address = reinterpret_cast<uintptr_t>(data);
  uint64_t flags = 0;
  if (!readValue(address, flags)) return false;
  constexpr uint64_t kSprintingBit = 1ull << 3;
  flags = value ? (flags | kSprintingBit) : (flags & ~kSprintingBit);
  iovec local{&flags, sizeof(flags)};
  iovec remote{reinterpret_cast<void *>(address), sizeof(flags)};
  return process_vm_writev(getpid(), &local, 1, &remote, 1, 0) ==
         static_cast<ssize_t>(sizeof(flags));
}

} // namespace gameecs
