# Pitlane

Pitlane é um mod Fabric para Minecraft Java 26.1.2 com três carros e uma simulação própria por roda, executada no servidor. A condução procura aproximar a resposta de um simulador de pista, mas não reproduz nem contém código-fonte do Assetto Corsa EVO. A análise local com Ghidra serviu apenas para orientar algumas escolhas comportamentais; a física do mod é uma implementação independente.

## Requisitos

- Minecraft Java Edition 26.1.2
- Fabric Loader 0.19.5 ou superior
- Fabric API 0.155.3+26.1.2
- Java 25

## Instalação

1. Instala Fabric Loader e Fabric API para Minecraft 26.1.2.
2. Copia `build/libs/pitlane-0.1.0.jar` para a pasta `mods` do cliente e do servidor.
3. Arranca o jogo. Os três spawners aparecem no separador criativo **Pitlane — Carros**.

## Condução

- Usa o spawner de tração dianteira, traseira ou integral para criar um carro.
- Clica com o botão direito no carro para entrar.
- **W** acelera, **S** trava, **A/D** viram e **Espaço** usa o travão de mão.
- Usa **Shift** para sair.
- As teclas podem ser alteradas nas definições de controlos, na categoria Pitlane.

O cliente envia os controlos com sequência monotónica; o servidor aceita comandos apenas do ocupante do carro, limita os valores e expira entradas antigas. O cliente só interpola e apresenta o estado recebido.

## Física e limites deste marco

O servidor avança seis subpassos de 1/120 s por tick. O modelo inclui suspensão, carga normal, escorregamento combinado de pneus, motor, caixa automática, diferenciais, travões e consulta de aderência pelo bloco. Os três carros usam configurações JSON em `data/pitlane/cars/` e podem ser substituídos por packs de dados.

O primeiro marco usa um modelo visual original e genérico, sem modelos ou texturas do Assetto Corsa. Não inclui ainda danos, som, mudança manual, animações de suspensão visíveis, colisão entre carros ou afinação baseada em telemetria comparativa. Os parâmetros são aproximações configuráveis, não uma recuperação da implementação original do jogo.

## Compilar

Com Java 25 instalado, executa `gradlew.bat jar`. O artefacto é criado em `build/libs/`.
