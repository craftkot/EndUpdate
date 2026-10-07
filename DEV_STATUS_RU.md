# DEV STATUS — End Update 0.1.0

## Готово по логике
- chorus wood family: да
- End Stone -> vanilla stone tools: да, через tag
- End Zombie: да
- End Skeleton + teleport projectile: да
- End Creeper: да
- End Spider: да
- potion/splash/lingering/tipped arrow teleport chain: да
- natural End spawns: да
- custom End trial arena generation: да
- 3-wave trial state machine: да
- trial mob ownership / arena recovery: да
- reward loot: да
- RU/EN localization: да

## Проверено статически
- JSON parse: OK (127 файлов на момент проверки)
- зарегистрированные блоки: 14
- кастомные entity types: 5 (4 моба + projectile)
- blockstate/item model/loot coverage: OK
- локальные ссылки на custom models/textures: OK

## Не подтверждено запуском
- Java compilation против реального Forge 47.4.10
- client startup
- dedicated server startup
- worldgen in a fresh End
- renderer UV quality in-game
- balance/frequency of natural spawns and trials

Причина: рабочая среда не содержит Gradle/Forge dependencies и не может получить MDK с Forge Maven.
