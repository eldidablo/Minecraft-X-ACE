# Pitlane — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** Criar o primeiro marco jogável do mod Pitlane para Minecraft Java 26.1.2/Fabric com três carros de física por roda.

**Arquitetura:** Um catálogo JSON alimenta uma entidade de carro e um solver Java independente. O servidor valida os comandos e integra seis subpassos físicos por tick; o cliente envia entradas e renderiza/interpola o estado.

**Tecnologias:** Java 25, Minecraft 26.1.2, Fabric Loader 0.19.5, Fabric Loom `1.18-SNAPSHOT`, Fabric API `0.155.3+26.1.2`, Gradle Wrapper 9.7.1. O projeto usa nomes oficiais do Minecraft e source sets `main`/`client`.

**Especificação:** `docs/superpowers/specs/2026-10-13-assetto-corsa-style-driving-mod-design.md`

## Restrições globais

- Alvo: Minecraft Java Edition 26.1.2 com Fabric.
- Java de compilação: 25.
- Unidades internas SI; um bloco Minecraft representa um metro.
- A simulação física corre no servidor em seis subpassos de `1/120 s` por tick nominal de 20 TPS.
- O servidor decide entradas aceites, ocupantes, física, colisões e estado do carro.
- Três definições iniciais em JSON representam tração dianteira, traseira e integral.
- A lógica é escrita de forma independente; a pseudocódigo Ghidra apenas orienta o comportamento.
- Modelos e texturas são originais do mod.

## Review Focus

- JSON ausente, inválido ou duplicado: o catálogo rejeita o recurso inválido, regista o erro e mantém as definições válidas. **Tarefa 2.**
- Roda sem contacto ou carga normal nula: a roda não gera força de pneu e o chassis continua finito sob gravidade. **Tarefa 4.**
- Chunk descarregado ou fronteira do mundo durante uma consulta: o solver não força carregamento de chunk nem consulta dados inexistentes. **Tarefa 5.**
- Payload com remetente sem lugar de condutor, sequência antiga ou floats NaN/Inf: o servidor ignora-o e zera a entrada expirada. **Tarefa 6.**
- Condutor sai ou desconecta durante aceleração: o carro deixa de receber acelerador no prazo de um tick de rede. **Tarefas 5 e 6.**

---

## Mapa de ficheiros

- `settings.gradle`, `build.gradle`, `gradle.properties`, `gradle/wrapper/*`, `gradlew.bat`, `.gitignore`: projeto Gradle/Loom e dependências.
- `src/main/resources/fabric.mod.json`: metadados e entrypoints.
- `src/main/java/dev/pitlane/PitlaneMod.java`: inicialização comum.
- `src/main/java/dev/pitlane/car/CarDefinition.java`, `CarDefinitionCatalog.java`, `CarDefinitionReloadListener.java`: schema, validação e catálogo JSON.
- `src/main/java/dev/pitlane/physics/CarControl.java`, `VehicleState.java`, `WheelState.java`, `EngineSolver.java`, `DrivetrainSolver.java`, `SuspensionSolver.java`, `TireModel.java`, `CarPhysicsEngine.java`, `SurfaceQuery.java`: estado e solver.
- `src/main/java/dev/pitlane/entity/CarEntity.java`, `ModEntities.java`: entidade física e registo.
- `src/main/java/dev/pitlane/item/CarSpawnerItem.java`, `ModItems.java`: itens de criação.
- `src/main/java/dev/pitlane/network/CarInputPayload.java`, `CarNetworking.java`: payload e validação no servidor.
- `src/client/java/dev/pitlane/client/PitlaneClient.java`, `CarInputClient.java`, `CarRenderState.java`, `CarModel.java`, `CarRenderer.java`: controlos locais e renderização.
- `src/main/resources/data/pitlane/cars/front_drive.json`, `rear_drive.json`, `all_wheel_drive.json`: três carros iniciais.
- `src/main/resources/assets/pitlane/**`: modelos, texturas, traduções e ícones próprios.
- `README.md`: requisitos, instalação e controlos.

## Tarefas

### Tarefa 1: base Fabric e entrypoints

**Ficheiros:**

- Criar: ficheiros Gradle, wrapper, `.gitignore`, `fabric.mod.json`, `PitlaneMod.java`, `PitlaneClient.java`.

**Interfaces:**

- Produz: `dev.pitlane.PitlaneMod implements ModInitializer` e `dev.pitlane.client.PitlaneClient implements ClientModInitializer`.
- Dependências: nenhuma.

- [x] Criar o projeto Loom com plugin `net.fabricmc.fabric-loom`, `splitEnvironmentSourceSets()`, `mods { "pitlane" ... }`, Gradle 9.7.1 e `rootProject.name = 'pitlane'`.
- [x] Fixar em `gradle.properties` `minecraft_version=26.1.2`, `loader_version=0.19.5`, Loom da linha 1.18, `fabric_api_version=0.155.3+26.1.2`, `version=0.1.0` e `group=dev.pitlane`.
- [x] Configurar Java release/target 25 e `fabric.mod.json` com `fabricloader >=0.19.5`, `minecraft ~26.1`, `java >=25`, `fabric-api *`; adicionar os dois entrypoints.
- [x] Executar a tarefa `jar`. Esperado: `BUILD SUCCESSFUL` e jar em `build/libs/`.

### Tarefa 2: definições de carro e carregamento JSON

**Ficheiros:**

- Criar: `car/CarDefinition.java`, `car/CarDefinitionCatalog.java`, `car/CarDefinitionReloadListener.java` e os três JSON de `data/pitlane/cars/`.

**Interfaces:**

- `CarDefinition` agrega `ChassisConfig`, `WheelLayout`, `SuspensionConfig`, `TireConfig`, `EngineConfig`, `TransmissionConfig`, `DrivetrainConfig`, `BrakeConfig` e `SurfaceGripConfig`.
- `CarDefinitionCatalog.find(Identifier id): Optional<CarDefinition>` e `snapshot(): Map<Identifier, CarDefinition>`.
- O listener lê recursos `data/<namespace>/cars/*.json`, valida campos/unidades/valores finitos e publica um snapshot imutável.

- [x] Definir records Java para chassis, quatro rodas, suspensão, pneu, motor, transmissão, diferencial, travões e aderência de superfície, mantendo os campos da especificação.
- [x] Implementar decodificação e validação por recurso; uma definição inválida fica fora do snapshot e gera um erro com o ID do recurso.
- [x] Criar `front_drive.json`, `rear_drive.json` e `all_wheel_drive.json` com configurações próprias e relações de tração correspondentes.
- [x] Executar `.\gradlew.bat jar`. Esperado: `BUILD SUCCESSFUL`.

### Tarefa 3: estado físico e powertrain

**Ficheiros:**

- Criar: `physics/CarControl.java`, `VehicleState.java`, `WheelState.java`, `EngineSolver.java`, `DrivetrainSolver.java`.

**Interfaces:**

- `CarControl(float throttle, float brake, float steering, boolean handbrake)`.
- `VehicleState(Vector3d position, Quaterniond orientation, Vector3d velocity, Vector3d angularVelocity, double engineRpm, int gear, List<WheelState> wheels)`.
- `EngineSolver.evaluate(EngineConfig config, double rpm, CarControl input, double dt): EngineOutput`.
- `DrivetrainSolver.distribute(DrivetrainConfig config, EngineOutput engine, int gear, double[] wheelAngularVelocity): double[4]` devolve torque por roda.

- [x] Definir estados imutáveis para chassis, rodas e comandos; inicializar sempre quatro rodas e normalizar controlos para os intervalos definidos.
- [x] Implementar curva de torque, resposta do acelerador, coast, rotação, relações e seleção automática de mudanças.
- [x] Distribuir torque para o eixo motriz e aplicar o comportamento de diferencial configurado aos valores por roda.
- [x] Executar a tarefa `jar`. Esperado: `BUILD SUCCESSFUL`.

### Tarefa 4: contacto, suspensão, pneus e integração do chassis

**Ficheiros:**

- Criar: `physics/SurfaceQuery.java`, `SuspensionSolver.java`, `TireModel.java`, `CarPhysicsEngine.java`.

**Interfaces:**

- `SurfaceQuery.castWheel(int wheel, Vector3d origin, Vector3d direction, double maxTravel): WheelContact`.
- `CarPhysicsEngine.step(CarDefinition car, VehicleState state, CarControl input, SurfaceQuery surface, double dt): VehicleState` executa um subpasso.
- `TireModel.evaluate(TireConfig config, double longitudinalSlip, double lateralSlip, double normalLoad, double surfaceGrip): TireForce`.

- [x] Calcular compressão, velocidade, força de mola/amortecedor, batentes e barra estabilizadora para cada contacto.
- [x] Calcular escorregamento longitudinal/lateral e forças locais; limitar a força combinada com a p-norm configurada, tratando carga nula e valores não finitos.
- [x] Somar forças e momentos das quatro rodas, motor, travões, gravidade e resistência do ar; integrar posição, velocidade e orientação com `dt`.
- [x] Executar a tarefa `jar`. Esperado: `BUILD SUCCESSFUL`.

### Tarefa 5: entidade, contactos do mundo e criação dos carros

**Ficheiros:**

- Criar: `entity/CarEntity.java`, `ModEntities.java`, `item/CarSpawnerItem.java`, `ModItems.java`.
- Recursos: adicionar modelos de item dos três spawners.

**Interfaces:**

- `CarEntity` guarda `Identifier definitionId`, `VehicleState`, condutor e quatro valores de rotação/esterço visual.
- `CarEntity` executa seis chamadas a `CarPhysicsEngine.step(..., 1.0 / 120.0)` no tick de servidor.
- `CarSpawnerItem` associa cada item a um `definitionId` e só cria entidades no servidor.

- [x] Registar `EntityType<CarEntity>` e três itens; implementar entrada por clique direito e saída por Shift.
- [x] Adaptar `SurfaceQuery` ao raycast de formas de colisão de blocos carregados; parar ou limitar a simulação quando a região necessária não está carregada.
- [x] Integrar movimento e colisão do corpo com o mundo e sincronizar estado de chassis/rodas para clientes.
- [x] Executar a tarefa `jar`. Esperado: `BUILD SUCCESSFUL`.

### Tarefa 6: controlos configuráveis e autoridade de rede

**Ficheiros:**

- Criar: `network/CarInputPayload.java`, `CarNetworking.java`, `client/CarInputClient.java`.
- Modificar: `CarEntity.java`, `PitlaneClient.java`, `fabric.mod.json` se o registo de payloads exigir inicialização adicional.

**Interfaces:**

- `CarInputPayload(int entityId, long sequence, float throttle, float brake, float steering, boolean handbrake)`.
- Cliente: ler as teclas configuráveis com defaults W/S/A/D/Espaço e enviar um payload por tick quando conduz.
- Servidor: aceitar o payload apenas do ocupante condutor autenticado; descartar sequência antiga, valores não finitos ou alvo inválido.

- [x] Registar payload com `PayloadTypeRegistry.serverboundPlay` e o handler no contexto de jogo do servidor.
- [x] Ligar key mappings a `CarInput` e enviar a sequência monotónica; Shift continua a desmontar através do comportamento da entidade.
- [x] Aplicar clamp ao input, validar proprietário/lugar, expirar comandos após um tick e zerar input em saída/desconexão.
- [x] Executar `jar`. Resultado: `BUILD SUCCESSFUL`.

### Tarefa 7: representação do carro e distribuição

**Ficheiros:**

- Criar: `client/CarRenderState.java`, `CarModel.java`, `CarRenderer.java`, recursos em `assets/pitlane/` e `README.md`.
- Modificar: `PitlaneClient.java`, `CarEntity.java` para expor dados visuais sincronizados.

**Interfaces:**

- `CarRenderState` fornece ângulo de direção dianteira, fase de rotação por roda e estado interpolado do chassis.
- `CarRenderer` regista o modelo original e renderiza carrocerias e rodas; o código fica no source set `client`.

- [x] Criar modelo de carro genérico com rodas que esterçam/rodam e texturas próprias; não importar recursos do Assetto Corsa EVO.
- [x] Registar render state/renderer conforme a API de entidades de 26.1.2 e adicionar traduções `pt_pt` e `en_us`.
- [x] Documentar instalação, três spawners, entrada/saída e controlos no README.
- [x] Executar `jar`. Resultado: `BUILD SUCCESSFUL`; jar em `build/libs/`.

## Auto-revisão do plano

- **Cobertura:** bootstrap — T1; definições JSON — T2; motor/caixa/diferencial — T3; suspensão/pneus/integração por roda — T4; entidade, interação e colisões — T5; rede/teclas — T6; modelo, traduções e distribuição — T7.
- **Interfaces:** IDs e configurações de T2 alimentam o solver T3–T4; `VehicleState`/`CarControl` passam ao solver; T5 executa o solver e fornece contactos do mundo; T6 entrega inputs validados a T5; T7 consome apenas estado de apresentação sincronizado.
- **Review Focus:** os cinco riscos no início estão atribuídos às tarefas responsáveis e têm comportamento esperado definido.
- **Proporção:** sete tarefas verticais correspondem ao único primeiro marco jogável e mantêm os limites de evidência e as escolhas já aprovadas.
- **Compilação:** cada tarefa termina com `jar`, que compila e empacota o mod. O plano não acrescenta nem executa testes automatizados.

## Referências oficiais para o scaffold

- [Setup Fabric](https://docs.fabricmc.net/develop/getting-started/setting-up) — JDK 25 para Minecraft 26.1.
- [Fabric Loom](https://docs.fabricmc.net/develop/loom/) — plugin `net.fabricmc.fabric-loom` para Minecraft 26.1 ou superior.
- [Exemplo Fabric 26.1](https://github.com/FabricMC/fabric-example-mod/tree/26.1) — Gradle 9.7.1, Loom 1.18-SNAPSHOT, Loader 0.19.5, source sets `main`/`client`.
- [API Fabric 0.155.3 para 26.1.2](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.155.3%2B26.1.2/).
- [Entidades Fabric para 26.1.2](https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/entities/first-entity.md).
- [Migração da API Fabric 26.1](https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/porting/fabric-api.md) — nomes atuais de payloads e source APIs.
