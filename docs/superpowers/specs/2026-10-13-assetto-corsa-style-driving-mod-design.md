# Design: mod Fabric de condução inspirada no Assetto Corsa EVO

**Estado:** para revisão do utilizador  
**Data:** 2026-10-13

## Objetivo

Criar um mod de carros para Minecraft Java Edition 26.1.2 com Fabric. O manuseamento deve usar como referência observável a arquitetura de física encontrada no executável local do Assetto Corsa EVO, com implementação nova em Java. A meta de condução é transmitir diferenças de aderência, transferência de carga, suspensão, tração e travagem ao jogador.

O alvo é um primeiro marco jogável com três carros configuráveis: tração dianteira, traseira e integral. Os carros partilham a implementação e têm parâmetros próprios em JSON.

## Evidência do Ghidra e limites

A análise estática foi feita no `AssettoCorsaEVO.exe` PE64 com Ghidra 12.1.4. O hash SHA-256 registado foi `ab11fc2a35ef9c2c61c6e0bcbdeb755b0d7787d101c3fdebfee12df3eadedbcb`. O relatório de pesquisa está em `.analysis/assetto-corsa-evo-physics-findings.md`.

Observações concretas do executável:

- RTTI identifica `SCTM` como implementação concreta de `ITyreModel` encontrada nesta pesquisa. O método virtual encaminha para `0x1425086e0`.
- O método do pneu inicializa nove resultados. Os fluxos de cálculo identificam as saídas 0 e 1 como forças longitudinais e laterais. O resultado 8 combina componentes de escorregamento usando um auxiliar p-norm configurável. Os significados das saídas 2 a 7 continuam por confirmar.
- O auxiliar p-norm calcula `(|x|^p + |y|^p)^(1/p)` para `p > 0`, usando magnitude euclidiana quando `p <= 0`. Outro auxiliar aplica um envelope de saturação às forças.
- O passo físico do carro percorre quatro posições de roda. A telemetria expõe curso e velocidade da suspensão, batentes de compressão/extensão e força. A equação exata da mola e do amortecedor não ficou isolada.
- Os dados serializados nomeiam curvas do motor, diferencial, suspensão e pneus. A rotina do motor mostra smoothing e limites; a rotina observada do diferencial prepara configurações, mas não identifica por completo o solver em runtime.

Ghidra produziu pseudocódigo a partir de instruções compiladas. O código-fonte original não foi recuperado. Os resultados guiam escolhas de comportamento; fórmulas e valores não confirmados serão decisões independentes da implementação Java.

## Experiência do jogador

- Três itens de criação, um para cada configuração de tração, criam o carro correspondente à frente do jogador.
- Clique direito num carro livre para entrar no lugar do condutor; Shift desmonta.
- WASD controla acelerador, travagem e direção. Espaço aplica o travão de mão.
- As mudanças de caixa são automáticas no primeiro marco. A configuração do carro determina relações, curva de torque e diferencial.
- A representação visual usa modelos e texturas originais do mod, com rodas que esterçam e rodam. Os carros são genéricos e identificados por nomes próprios do mod.

## Arquitetura e rede

- Entidade Java de carro guarda o identificador da configuração e o estado persistente do veículo.
- Definições JSON fornecem geometria, massa, parâmetros da suspensão, pneus, motor, caixa, diferencial e travões. As três definições iniciais são recursos do mod.
- O servidor é a autoridade sobre ocupantes, entradas aceites, física, colisões, posição e velocidade.
- O cliente envia apenas o estado limitado dos comandos e interpola os estados recebidos do servidor para renderização.
- O servidor aceita comandos do condutor atual, limita valores e rejeita sequências antigas ou entradas de outros jogadores.

## Núcleo físico

- Unidades internas SI, com um bloco Minecraft tratado como um metro.
- O servidor executa seis subpassos de `1/120 s` por tick Minecraft nominal de 20 TPS.
- Cada roda consulta o terreno com um raio de contacto. A compressão, velocidade, mola, amortecimento em compressão/retorno e batentes fornecem a carga normal.
- A velocidade relativa no ponto de contacto dá escorregamento longitudinal e lateral por roda. Curvas do pneu geram forças locais; uma p-norm configurável limita a força combinada segundo a aderência disponível e a carga normal.
- As forças são ajustadas por configuração de pneu e categoria de superfície. O servidor agrega forças e momentos das quatro rodas, resistência do ar, gravidade e travões para integrar translação e rotação do chassis.
- O motor usa curva de torque, resposta ao acelerador, desaceleração, rotação e relações da caixa. A configuração de tração reparte o torque para o eixo ou eixos motrizes; diferencial e travões aplicam os seus limites por roda.
- As consultas das rodas seguem a forma de colisão dos blocos. O corpo do veículo também respeita colisões do mundo.

## Formato de dados de carro

Cada JSON contém, no mínimo:

- massa, dimensões, posição do centro de massa e inércia do chassis;
- posições das rodas, distância entre eixos, largura de via, curso e batentes;
- rigidez de mola, amortecimento em compressão/retorno e barra estabilizadora por eixo;
- parâmetros de curva do pneu, aderência, sensibilidade à carga, pressão e faixa térmica de referência;
- curva de torque, resposta do acelerador, rotações de mudança, relações de caixa e tipo de tração;
- ajuste de diferencial, torque dos travões e força do travão de mão;
- multiplicadores de aderência para superfícies.

Os três carros iniciais usam valores de referência criados para o mod. A estrutura permite adicionar carros por dados sem duplicar o solver.

## Critérios de aceitação do primeiro marco

1. O mod inicia em Minecraft 26.1.2 com Fabric e regista as três definições e itens de criação.
2. Um jogador cria, conduz e desmonta de cada carro num mundo local.
3. Num servidor dedicado, o jogador condutor envia comandos e todos os clientes veem o mesmo estado físico interpolado.
4. A diferença entre tração dianteira, traseira e integral é perceptível em aceleração e perda de aderência.
5. Travagem, travão de mão, esterço, compressão da suspensão e contacto das rodas alteram o movimento do carro.
6. As configurações são carregadas de JSON e podem ser ajustadas sem alterar o código do solver.

## Fora do primeiro marco

O primeiro marco não inclui modelos, texturas, código ou dados de carros extraídos do Assetto Corsa EVO. Também não inclui editor de garagem, condução manual da caixa, danos, pneus deformáveis, ABS/controlo de tração configurável ou simulação de ocupantes além do condutor. A arquitetura JSON deixa espaço para esses sistemas numa fase posterior.

## Riscos e calibração

Minecraft atualiza entidades a 20 TPS e não oferece um solver rígido de veículo. Seis subpassos melhoram a estabilidade do cálculo, mas a colisão e a renderização continuam sujeitas ao mundo e ao ciclo de jogo. A semelhança com Assetto Corsa EVO será uma meta de afinação, sem equivalência numérica demonstrada. O primeiro protótipo deverá ser ajustado por comportamento observável — travagem, aceleração, subviragem, sobreviragem e resposta às irregularidades — usando os limites anotados no relatório Ghidra.

## Revisão interna

- O âmbito corresponde às decisões já aprovadas: Java, Fabric, servidor autoritativo, física por roda, seis subpassos de `1/120 s`, pneus com escorregamento combinado e três carros em JSON.
- A implementação é identificada como independente; o relatório separa observações do executável das inferências de design.
- Os controlos, interação, autoridade de rede, formato de dados e critérios jogáveis estão definidos para permitir planeamento e implementação após a revisão desta especificação.
- Os significados desconhecidos das saídas SCTM e as equações não isoladas de suspensão/diferencial continuam marcados como desconhecidos.
