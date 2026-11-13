#pragma once

#include <cstdint>

#define COMBAT_STATE_EXPORT [[gnu::visibility("default")]]

namespace combatstate {

COMBAT_STATE_EXPORT void suppressSprint(std::uint32_t ms);
COMBAT_STATE_EXPORT void clearSprintSuppress();
COMBAT_STATE_EXPORT bool sprintSuppressed();

} // namespace combatstate
