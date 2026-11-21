# MIX VENDAS 3D — Relatório F2-E

## Correspondência das regras

18 regras globais e 33 das 37 exceções foram mapeadas no Compose. As três posições de Excluir em Produtos e o segundo cabeçalho de Importar/exportar ficaram pendentes. Nessa tela a ação existente altera o status. Os campos de índices de listas repetidas referem-se às primeiras posições da lista exibida dentro do componente correspondente. As duas ocorrências de cartão do Fechamento correspondem aos indicadores de Débito e Crédito. Em VENDAS, a posição corresponde ao indicador do método no cartão do registro.

A escala usa a base Compose quando há um tamanho Android existente. Nas posições novas, a base do JSON para segunda dimensão orienta o tamanho Android. Os tamanhos da tabela descrevem a caixa do PNG antes da margem alfa interna. O espaço disponível pode impor limites adicionais em janelas menores. As exceções substituem a porcentagem global antes de aplicar o limite do contêiner.

### Exceções por posição

| Tela, desenho, contexto e posição | Solicitado | Referência HTML | Android (dp) |
|---|---:|---:|---|
| vender / dinheiro_notas / payment #1 | 160% | 70,4 dp | 40 → até 64 · limite de 42% da largura interna do botão |
| vendas / cartao_bancario / small #1 | 180% | 57,6 dp | 32 → 57,6 · indicador ao lado da forma de pagamento |
| fechamento / maquininhas / small #1 | 220% | 70,4 dp | 50 → 96 · preserva espaço para título e seta (alvo 110) |
| fechamento / cartao_bancario / small #2 | 220% | 70,4 dp | 44 → 72 · preserva largura para valores e rótulos (alvo 96,8) |
| fechamento / cartao_bancario / small #1 | 220% | 70,4 dp | 44 → 72 · preserva largura para valores e rótulos (alvo 96,8) |
| fechamento / pix / pixClosing #1 | 220% | 61,6 dp | 28 → 61,6 · novo PIX nas linhas de pagamento |
| fechamento / dinheiro_notas / small #1 | 220% | 70,4 dp | 44 → 72 · preserva largura para valores e rótulos (alvo 96,8) |
| mais / maquininhas / more #1 | 200% | 128,0 dp | 64 → até 128 · limitado a 38% da largura interna do cartão |
| mais / importar_exportar / more #1 | 200% | 128,0 dp | 64 → até 128 · limitado a 38% da largura interna do cartão |
| mais / atualizar_sistema / more #1 | 200% | 128,0 dp | 64 → até 128 · limitado a 38% da largura interna do cartão |
| mais / produtos / more #1 | 200% | 128,0 dp | 64 → até 128 · limitado a 38% da largura interna do cartão |
| produtos / editar / action #1 | 160% | 57,6 dp | 36 → 57,6 · botão de 58 dp, nas três primeiras posições visíveis |
| produtos / excluir / action #1 | 160% | 57,6 dp | Pendente. Cadastro possui apenas o comando Ativar/Desativar nesse local. |
| produtos / editar / action #2 | 160% | 57,6 dp | 36 → 57,6 · botão de 58 dp, nas três primeiras posições visíveis |
| produtos / excluir / action #2 | 160% | 57,6 dp | Pendente. Cadastro possui apenas o comando Ativar/Desativar nesse local. |
| produtos / excluir / action #3 | 160% | 57,6 dp | Pendente. Cadastro possui apenas o comando Ativar/Desativar nesse local. |
| produtos / editar / action #3 | 160% | 57,6 dp | 36 → 57,6 · botão de 58 dp, nas três primeiras posições visíveis |
| nomes / editar / action #1 | 160% | 57,6 dp | 36 → 52 · conserva a largura do texto e alvo de toque (alvo 57,6) |
| nomes / editar / action #2 | 160% | 57,6 dp | 36 → 52 · conserva a largura do texto e alvo de toque (alvo 57,6) |
| nomes / editar / action #3 | 160% | 57,6 dp | 36 → 52 · conserva a largura do texto e alvo de toque (alvo 57,6) |
| nomes / excluir / action #3 | 160% | 57,6 dp | 36 → 52 · conserva a largura do texto e alvo de toque (alvo 57,6) |
| nomes / excluir / action #2 | 160% | 57,6 dp | 36 → 52 · conserva a largura do texto e alvo de toque (alvo 57,6) |
| nomes / excluir / action #1 | 160% | 57,6 dp | 36 → 52 · conserva a largura do texto e alvo de toque (alvo 57,6) |
| maquininhas / maquininhas / section #1 | 200% | 96,0 dp | 50 → 100 · cabeçalho principal |
| maquininhas / maquininhas / list #1 | 160% | 64,0 dp | 40 → 64 · título das duas primeiras linhas da listagem |
| maquininhas / maquininhas / list #2 | 160% | 64,0 dp | 40 → 64 · título das duas primeiras linhas da listagem |
| maquininhas / editar / action #1 | 160% | 57,6 dp | 36 → 48 · as três ações da linha compartilham a largura (alvo 57,6) |
| maquininhas / editar / action #2 | 160% | 57,6 dp | 36 → 48 · as três ações da linha compartilham a largura (alvo 57,6) |
| maquininhas / excluir / action #1 | 160% | 57,6 dp | 36 → 48 · as três ações da linha compartilham a largura (alvo 57,6) |
| maquininhas / excluir / action #2 | 160% | 57,6 dp | 36 → 48 · as três ações da linha compartilham a largura (alvo 57,6) |
| maquininhas / cartao_bancario / section #1 | 180% | 86,4 dp | 48 → 86,4 · cabeçalho de taxas por bandeira |
| maquininhas / cartao_bancario / list #1 | 170% | 68,0 dp | 38 → 64,6 · três primeiras linhas de bandeiras padrão no editor |
| maquininhas / cartao_bancario / list #2 | 170% | 68,0 dp | 38 → 64,6 · três primeiras linhas de bandeiras padrão no editor |
| maquininhas / cartao_bancario / list #3 | 170% | 68,0 dp | 38 → 64,6 · três primeiras linhas de bandeiras padrão no editor |
| atualizar / atualizar_sistema / section #1 | 180% | 86,4 dp | 52 → 93,6 · cabeçalho da tela |
| importar / importar_exportar / section #1 | 180% | 86,4 dp | 52 → 93,6 · cabeçalho do cartão de backup |
| importar / importar_exportar / section #2 | 180% | 86,4 dp | Pendente. O Compose só tem uma ocorrência do PNG em cabeçalho · os ícones de seção são nuvens distintas. |

### Tamanhos globais por contexto

| Desenho | Solicitado | Exemplos de base → Android (dp) |
|---|---:|---|
| Início, Nova venda, VENDAS, Fechamento, Mais | 230% | Barra: 36 → alvo 82,8 · até 64 e limitado à largura da aba. No cálculo estático: até 56,4 por aba a 320 dp, 64 a partir de 358 dp. |
| Nova venda | 230% | Ação principal do Início: 40 → 92. |
| VENDAS | 230% | Métrica do Início: 52 → 119,6 · contador no Fechamento: 24 → 55,2 · estado vazio: 64 → 147,2 sujeito à largura. |
| Ver mais | 230% | Início e detalhes de VENDAS: 32 → 73,6. |
| Calendário | 220% | Cabeçalho de VENDAS: 38 → 83,6 · seletores de VENDAS e Fechamento: 40 → 88. |
| Moedas | 200% | Métrica do Início: 52 → 104 · total no Fechamento: 50 → 100 · filtro de VENDAS: 40 → 80. |
| Cédulas | 200% | Métrica do Início: 52 → 104 · subtotais compactos do Fechamento têm limite de 40 para o valor monetário. Pagamentos usam as exceções acima. |
| Pessoas | 200% | Cabeçalhos de Início e Fechamento: 48 → 96 · indicadores de backup: 36 → 72. |
| Nomes | 200% | Avatar: 40 → 80, com limite de 56 nas linhas de Nomes para conservar largura das ações e do texto · ação de cadastro: 36 → 72 · Mais: 64 → até 128. |
| Tarifas | 200% | Métrica do Início: 52 → 104 · subtotais compactos do Fechamento: 40 → 40 com limite · editor: cabeçalho 48 → 86,4 e linhas 38 → 66, com limite para o campo da taxa. |
| PIX | 200% | VENDAS: 28 → 56 · Nova venda: 28 → até 56 pelo limite de 42% do botão · Fechamento usa a exceção de 220%. |
| Produtos | 170% | CTAs: 36 → 61,2 · detalhe de VENDAS: 40 → 68 · estado vazio da venda: 64 → 108,8 · Mais usa a exceção de 200%. |
| Cartão | 155% | Nova venda: 40 → até 62, sujeito à largura do botão · linhas de bandeiras padrão após a terceira: 38 → 58,9. |
| Buscar | 150% | Campos da busca: 32 → 48 · guia do backup: 42 → 63 e 38 → 57 conforme o layout. |
| Editar e Excluir | 140% | VENDAS: 34 → 42 por causa dos rótulos ao lado · Nomes após a terceira posição: 36 → 48 com limite · Editar Produtos após a terceira posição: 36 → 50,4. Confirmação de exclusão de VENDAS: 48 → 67,2. |

A margem alfa dos PNGs e o `ContentScale.Fit` determinam o tamanho real do desenho dentro da caixa. Em telas compactas, a barra calcula a largura de cada aba uma vez descontados os respiros, e os cartões Mais e botões de pagamento reservam parte da largura para texto. O contador do Fechamento ocupa uma linha separada para deixar o valor total livre. Os limites das ações preservam as áreas de toque e os textos. Os pixels dos PNGs foram mantidos.

### Arquivos de produção

- `V19Components.kt`, `InicioVisualComponents.kt` e `MaisVisualComponents.kt`
- `FechamentoVisualComponents.kt`, `VendasVisualComponents.kt` e `VenderVisualComponents.kt`
- `NamesVisualComponents.kt`, `NamesScreen.kt`, `ProductsVisualComponents.kt` e `ProductsScreen.kt`
- `MachinesVisualComponents.kt` e `MachinesScreen.kt`
- `ImportExportVisualComponents.kt` e `UpdateSystemScreen.kt`
- `app/src/main/res/drawable-nodpi/mv3d_pix.png`

### Testes de interface ajustados

- `Stage2ANavigationTest.kt`, `Stage4B1HomeRealDataTest.kt` e `Stage8B1SalesEndToEndIntegrationTest.kt` rolam até a segunda linha antes de consultar seus indicadores. As métricas ficaram mais altas e, em telas pequenas, a lista ainda não compunha essa linha na posição inicial.
- `Stage3B2ASalesFlowTest.kt` rola até o resumo antes de verificar o contador de vendas. Os filtros ampliados ocuparam mais espaço acima do resumo.
- Foram mantidos os valores esperados, as verificações de fluxos reais e os ajustes da F2-D em SQLite, Room e Compose.

### Inspeção visual

Foram feitas capturas no Pixel 10 emulado, com 1080 × 2424 px. As caixas de ícone da barra mediram 168 × 168 px (64 dp) e ficaram separadas, com os cinco rótulos legíveis. Foram examinados Início, os cartões de Mais, o cadastro vazio de Maquininhas, a seleção de pagamentos em Nova venda, VENDAS e Fechamento. O PIX 3D foi visto no botão de pagamento em Nova venda. A revisão das capturas levou ao alinhamento dos rótulos das métricas e da altura dos botões Dinheiro e PIX, além de uma terceira linha para as descrições dos cartões Mais.

O APK final foi instalado no mesmo emulador. As novas capturas confirmaram o alinhamento dos rótulos e valores das métricas do Início, e a mesma altura para as opções Dinheiro e PIX. A inspeção visual dos indicadores em registros preenchidos e das ações por linha ficou pendente. Os estados preenchidos são exercitados pelos testes automatizados.

### Resultado das verificações

| Execução forçada da suíte | Aprovados | Reprovados | Ignorados |
|---|---:|---:|---:|
| Primeira rodada | 391 | 11 | 0 |
| Segunda rodada | 401 | 1 | 0 |
| Rodada final | 402 | 0 | 0 |

A primeira rodada localizou verificações de elementos ainda fora da área composta em Início e VENDAS. A segunda localizou um timeout em `Stage2B1UiIntegrationTest.kt` aguardando o aviso de cadastro de produto. O teste preserva a verificação do clique e agora espera o fechamento do editor, rola até o aviso e exige que ele esteja visível. A execução final de `:app:testDebugUnitTest --rerun-tasks --console=plain` retornou código 0 em 9 min 46 s. A F2-B teve 6 aprovações e zero falhas. [Relatório da suíte](<C:/Users/olive/Downloads/Studio/Mix Vendas 3D/app/build/reports/tests/testDebugUnitTest/index.html>)

A build independente `:app:assembleDebug --console=plain` retornou código 0 em 1 min 14 s. O APK Debug tem 24.717.880 bytes. [APK Debug](<C:/Users/olive/Downloads/Studio/Mix Vendas 3D/app/build/outputs/apk/debug/app-debug.apk>)

Os SHA-256 dos 20 PNGs originais coincidiram antes e depois. O recurso Android do PIX tem o mesmo SHA-256 do arquivo fornecido em `assets/mv3d_pix.png`. Ele é usado como indicador decorativo em Nova venda, VENDAS e Fechamento. O código de pagamento, o launcher, o fundo do castelo e a assinatura permaneceram intactos.

