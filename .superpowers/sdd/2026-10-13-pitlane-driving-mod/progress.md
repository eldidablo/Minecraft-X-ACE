# SDD ledger — plan: docs/superpowers/plans/2026-10-13-pitlane-driving-mod.md

## Objetivo
Entregar o primeiro marco instalável do mod Pitlane para Minecraft Java 26.1.2/Fabric, seguindo a especificação aprovada e implementando física própria inspirada nos indícios observados no Assetto Corsa EVO via Ghidra.

## Rulings
- Ruling: executar na pasta de trabalho atual sem Git/worktree — utilizador escolheu execução in-place e a pasta não é um repositório Git — custo se incorreto: sem isolamento por branch nem rollback Git.
- Ruling: executar compilação/empacotamento `jar`, sem adicionar ou correr testes automatizados — instrução de prioridade superior proíbe testes sem pedido explícito — custo se incorreto: sem cobertura automatizada de regressão/comportamento.
- Ruling: tratar as conclusões Ghidra como evidência comportamental limitada, sem alegar recuperação de código-fonte original — a análise não recuperou o código-fonte e a implementação é nova.
- Ruling: fixar Loom em `1.18.4` e mapear o ID do plugin diretamente ao módulo publicado — o marcador de `1.18-SNAPSHOT` é móvel e a resolução online falha neste ambiente; o módulo 1.18.4 e as dependências estão na cache local — custo se incorreto: não acompanha revisões posteriores de 1.18, embora o build fique fixo e reprodutível.

## Preflight de interfaces
| Produtor | Consumidor | Contrato verificado |
|---|---|---|
| Tarefa 1: inicialização comum/cliente | Tarefa 2: catálogo JSON | entrypoints Fabric podem carregar o catálogo/listener |
| Tarefa 2: `CarDefinition` e catálogo | Tarefas 3–5 | configs completas por chassis, roda, suspensão, pneu, motor, transmissão, diferencial, travões e superfície |
| Tarefa 3: estado, comandos, motor/transmissão | Tarefas 4–6 | `VehicleState`, `CarControl`, torque individual por roda |
| Tarefa 4: solver por subpasso | Tarefa 5: entidade/consulta de superfície | quatro contactos, `SurfaceQuery`, estado integrado |
| Tarefa 5: ocupante/estado visual/entidade | Tarefas 6–7 | autenticação do condutor e dados suficientes para rede/renderização |
| Tarefa 6: comandos autenticados e com sequência | Tarefa 5: entidade | servidor atualiza comando do veículo e expira input |
| Tarefa 7: estado de apresentação | renderização cliente | direção/rotação de rodas/interpolação sem solver no cliente |

Interfaces consistentes com o plano; adaptei `ResourceLocation` para `Identifier`, nome real em Mojang mappings 26.1.2.

## Estado
- Preflight: concluído; JDK 25.0.1 disponível; distribuição Gradle 9.7.1 presente na cache local.
- Tarefa 1: concluída — scaffold compilado/empacotado.
- Tarefa 2: concluída — parser e validação por recurso; presets FWD/RWD/AWD compilados.
- Tarefa 3: concluída — estado imutável, curva de torque, relações, mudanças automáticas e diferenciais.
- Tarefa 4: concluída — 4 contactos, suspensão, barra estabilizadora, slip combinado p-norm e integração 6DoF compilados.
- Tarefa 5: concluída — entidade, raycast sem carregar chunks, colisão do chassis, passageiros e spawners.
- Tarefa 6: concluída — payload de input e teclas configuráveis; servidor valida condutor/entidade/sequência/finitude e expira comandos após um tick; compilada/empacotada.
- Tarefa 7: concluída — render state interpolado, orientação completa, rodas animadas, 3 texturas originais e 3 ícones próprios, traduções e README; `BUILD SUCCESSFUL`.

## Registo de execução
### 2026-10-13 — arranque
- Reconfirmado o alvo Minecraft 26.1.2/Fabric, Java 25, API 0.155.3+26.1.2, servidor autoritativo, seis subpassos a 1/120 s e três configurações FWD/RWD/AWD.
- O workspace não tem Git; não foi inicializado Git, conforme escolha do utilizador.
- Wrapper pode ser gerado pela distribuição Gradle 9.7.1 em cache; compilação dependerá das dependências Loom/Minecraft disponíveis ou acessíveis.
- O sandbox bloqueou o download dos metadados Minecraft via Loom; estou a usar a cache de dependências partilhada e Gradle User Home no workspace, mantendo as chamadas Gradle no modo normal para permitir ao downloader Loom chegar ao manifesto Mojang.
- Tarefa 1 compilada com Java 25.0.1, Minecraft 26.1.2, API 0.155.3+26.1.2 e Loom 1.18.4. A truststore local recebeu apenas a raiz AVG já confiada pelo Windows; nenhum cacerts global foi alterado.
- O snapshot Loom exigiu resolução por alias em cache; o plugin foi fixado em 1.18.4 e mapeado ao módulo publicado para builds reproduzíveis neste workspace.
- Tarefa 2 terminou com `BUILD SUCCESSFUL`; catálogo substitui snapshot inteiro após reload e ignora definições inválidas individualmente.
- Tarefas 3 e 4 terminaram com `BUILD SUCCESSFUL`; o estado guarda quatro rodas e o solver integra translação/orientação em cada subpasso.
- Tarefa 5 terminou com `BUILD SUCCESSFUL`; superfície sem chunk carregado suspende o passo físico e o chassis passa pelo resolvedor de colisão de entidades do Minecraft.
- Tarefa 6 terminou com `BUILD SUCCESSFUL`; os mappings de tecla e a API de payload foram verificados na cache para Fabric/Minecraft 26.1.2 antes da implementação.
- Tarefa 7 terminou com `BUILD SUCCESSFUL`; o jar inclui renderer no source set cliente, modelos/ícones/texturas e traduções PT/EN.
