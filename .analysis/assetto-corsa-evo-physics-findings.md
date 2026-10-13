# Assetto Corsa EVO — notas da análise estática

Data da análise: 2026-10-13  
Alvo local: `AssettoCorsaEVO.exe`  
Método: Ghidra 12.1.4, importação PE64 x64 e descompilação focada de funções/vtables. A análise automática completa não terminou no tempo disponível; os resultados abaixo vêm de alvos concretos e dos respetivos corpos de função.

## Limite da recuperação

O executável não contém o código-fonte original em Java/C++. Ghidra reconstrói pseudocódigo a partir das instruções compiladas, com nomes de variáveis e estruturas incompletos. Estes resultados são evidência para modelar comportamento; não são uma recuperação literal do repositório privado da Kunos.

## Pneus: classe `SCTM`

- A RTTI identifica `SCTM` como implementação concreta de `ITyreModel` encontrada nesta pesquisa.
- A vtable de `SCTM` está em `0x1456057d8`. O método virtual de cálculo entra pelo thunk `0x1400fb3c0` e encaminha para `0x1425086e0`.
- O método recebe estruturas por ponteiro e inicializa nove valores de saída. O fluxo calcula as saídas 0 e 1 como componentes longitudinais e laterais da força do pneu; a saída 8 recebe uma expressão composta de escorregamento que inclui o auxiliar p-norm. As saídas 2 a 7 ainda não têm semântica confirmada por consumidores.
- A rotina devolve resultados nulos quando a carga normal é não positiva ou quando os sinais de entrada relevantes são todos nulos.
- O método combina duas componentes de escorregamento. O auxiliar `0x142508070` calcula uma norma p: `(|x|^p + |y|^p)^(1/p)` quando `p > 0`, com norma euclidiana como fallback. Isto dá uma forma configurável de saturação combinada longitudinal/lateral.
- O auxiliar `0x1425081b0` aplica um envelope de saturação às componentes de força, com um ramo de pico/limite e um fator de forma guardado no objeto do modelo. O código também escala por carga e fatores de superfície/condição; os nomes e unidades dos parâmetros ainda não foram recuperados.
- Há um ajuste auxiliar com clamps entre 0 e 1 que usa vários campos do modelo e entradas de runtime. A ligação exata a temperatura, desgaste e pressão não está provada só por esta rotina.
- O motor tem curvas distintas serializadas para torque/potência, desaceleração (`coast`), resposta ao acelerador e offset de coast. Uma rotina de estado em `0x14237cf5a` percorre entradas com stride `0xa8` e atualiza torque/estado com smoothing, limites de rotação e condição/dano; a recuperação de registos nesta rotina é parcial, portanto não usaremos a pseudocódigo como fórmula pronta.
- A rotina `0x1422e1160` prepara os parâmetros do diferencial e contém o caminho de ativação do spool. Os dados incluem ajustes power/coast, preload, faces de fricção e rampas de bloqueio; vimos defaults para campos nulos, mas os seus nomes/unidades exatos ainda vêm dos serializers.

Pseudocódigo e auxiliares:

- `.analysis/sctm-method.txt`
- `.analysis/sctm-helper-pseudocode.txt`
- `.analysis/sctm-vtable-pseudocode.txt`
- `.analysis/engine-update.txt`
- `.analysis/differential-spool.txt`
- `.analysis/sctm-drivetrain-pseudocode.txt`
- RTTI: `.analysis/ghidra-rtti-implementations-all.log`

## Passo físico e suspensão

- A função `0x1424ea8f0` ocupa `0x1424ea8f0..0x1424eb325` e contém a mensagem de erro `NetcarPhysicalBody::step requires initPhysics() first`, ligando-a ao ciclo físico do carro.
- O corpo itera quatro posições de roda e usa `delta time`, transformações vetoriais e chamadas de interface para consultar/aplicar quantidades físicas.
- A telemetria da mesma área inclui curso da suspensão `x`, velocidade `v`, limites de extensão/compressão (`droop_stop`, `bump_stop`) e força. Isto confirma que o modelo expõe e acompanha esses estados; ainda não isolámos a equação de mola/amortecedor porque parte do cálculo está em callbacks/auxiliares.
- As classes RTTI incluem `Suspension`, `SuspensionML`, `SuspensionMLNew`, `SuspensionStrut`, `SuspensionStrutMultilink`, `SuspensionWishboneCoil`, `SuspensionTrailingArm`, `SuspensionArm`, `SuspensionAxle` e `SuspensionLink`.

Pseudocódigo:

- `.analysis/suspension-step.txt`
- `.analysis/suspension-vtable-pseudocode.txt`
- `.analysis/ghidra-netcar-vtables.log`

## Configuração e telemetria confirmadas por nomes serializados

- Motor: `EngineData.powerCurve`, `coastCurve`, `throttleResponseCurve`, `gasCoastOffsetCurve`.
- Diferencial: `DifferentialData.thermal_performance_curve`, `mu_rpm_diff_curve`.
- Suspensão: `SuspensionSettingUnits.wheel_rate`, `helper_rate`, `helper_range`; dados de amortecedor com mapas `bump_lut` e `rebound_lut`; ajustes de amortecimento lento/rápido em compressão e retorno.
- Pneus: curva de performance térmica, curva pressão/mola, estados de escorregamento por roda/eixo, travagem disponível e torque de travão.

Esses nomes vêm de serializers/reflection e telemetria; por si só não provam qual código consome cada campo. A função `0x142500090` observada nesta fase serializa muitos campos e curvas de configuração, não é o solver do pneu.

## Próximas perguntas técnicas para o mod

1. Confirmar quais dos nove resultados de `SCTM` são forças/torques seguindo o código que os consome.
2. Isolar a resposta de mola, amortecedor, batentes e transferência de carga no passo físico.
3. Identificar o consumo das curvas de motor, caixa, diferencial e travões.
4. Fazer uma implementação Java independente, com unidades e integração próprias para o tick do Minecraft; não copiar pseudocódigo literalmente.
