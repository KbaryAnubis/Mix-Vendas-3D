# MIX VENDAS 3D — CONTEXTO E PASSAGEM PARA O CODEX NO PC

**Data:** 19/11/2026  
**Origem:** conversa ChatGPT “Branch · Criar app de vendas”  
**Link (pode exigir login e não fornece acesso automático):** https://chatgpt.com/c/6afe2487-e598-83e9-a6bc-8d28221cba96  
**Snapshot recomendado:** `mix-vendas-3d (1)(20261119-201744).zip` (exportado com identificação 20:17:44; veja `COMPARACAO_VERSOES.md`).

## 1. Objetivo imediato

Continuar o projeto Android **MIX VENDAS 3D** a partir da versão mais recente fornecida, **sem reiniciar etapas**, **sem perder alterações parciais da execução cancelada** e **sem sobrescrever um workspace local que possa estar mais novo**.

**Ordem:** (1) inspecionar o workspace/snapshot e comparar com o projeto atualmente aberto no PC, se houver; (2) corrigir a compilação Kotlin; (3) executar testes e build Debug; (4) completar somente ajustes de tamanho/alinhamento dos 20 ícones que ainda estiverem pendentes; (5) recompilar e testar, se alterado.

## 2. Estado conhecido / cautela com o histórico

- Projeto Android Kotlin + Jetpack Compose + Material 3 + banco Room, `applicationId` **`com.mixvendas3d.app`**, operação offline.
- A **Etapa F2-B** (CardBrand, taxas personalizadas e integrações em vendas/cadastros/backup/CSV/atualização do sistema) foi concluída **antes** dos ajustes de ícones. Na ocasião, houve relato de **106/106 testes aprovados e `assembleDebug` bem-sucedido**. Esse resultado **não valida** o estado atual pós-cancelamentos.
- Há 20 PNGs `mv3d_*.png` em `app/src/main/res/drawable-nodpi/` e mapeamento em `public/assets/aistudio/MAPA_DE_ICONES_c4f84ebd.json`.
- A integração visual anterior alterou 22 arquivos e foi cancelada. Depois, a etapa de **dimensionamento F2-C** também foi cancelada após editar 16 arquivos, listados em `COMPARACAO_VERSOES.md`. O ZIP das 20:17:44 contém alterações desses 16 arquivos em relação ao ZIP das 20:00:53.
- O usuário informou que **os dois últimos comandos sugeridos nesta conversa NÃO foram executados/gerados**: a instrução de retomada geral para 4.0 (F2-C2) e a instrução de reparo Kotlin (F2-C3). Tratá-los como propostas, não como etapas concluídas.
- Foram vistos em código após cancelamento ícones da barra inferior por volta de **36 dp**, tela Mais ~**64 dp**, botão Nova venda ~**40 dp**. Outros tamanhos foram alterados em pontos específicos, mas a revisão visual e a compilação ainda estão pendentes. Não assumir que tudo está correto.
- O projeto contém `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

## 3. Erros de compilação informados pelo usuário (não corrigidos)

Log do backend em 19/11/2026, 18:14 (horário exibido):

```
> Task :app:compileDebugKotlin FAILED
MainActivity.kt:40:62 Property delegate must have a 'getValue(...)' method
  ERROR CLASS: Symbol not found for ImportExportViewModel
MainActivity.kt:45:62 Property delegate must have a 'getValue(...)' method
  ERROR CLASS: Symbol not found for SystemUpdateViewModel
MixVendasApplication.kt:43:56 Property delegate must have a 'getValue(...)' method
  ERROR CLASS: Symbol not found for SystemUpdateRepository
MixVendasNavHost.kt:50:29 Property delegate must have a 'getValue(...)' method
  ERROR CLASS: Cannot infer argument for type parameter T
MixVendasShellViewModel.kt:531:26 Unresolved reference 'CsvExportValidationException'
Execution failed for task ':app:compileDebugKotlin'.
```

**Importante:** as classes/declaracões acima **estão fisicamente presentes** na versão exportada:

- `app/src/main/java/com/mixvendas3d/app/ui/screens/ImportExportViewModel.kt` — `class ImportExportViewModel`.
- `app/src/main/java/com/mixvendas3d/app/ui/screens/SystemUpdateViewModel.kt` — `class SystemUpdateViewModel`.
- `app/src/main/java/com/mixvendas3d/app/data/repository/SystemUpdateRepository.kt` — `interface SystemUpdateRepository` e classe `RoomSystemUpdateRepository`.
- `app/src/main/java/com/mixvendas3d/app/domain/export/CsvExportModels.kt` — `class CsvExportValidationException`.

O `getValue` pode ser **erro em cascata de símbolos não resolvidos**; investigar erros primários, `package`, imports, tipos, source sets e demais problemas de compilação. **Não substituir `by viewModels`/`by lazy` precipitadamente, não criar classes vazias/duplicadas, não remover tratamento de erro CSV.** Diferenciar erros no build remoto da versão realmente aberta no PC.

## 4. Regras para preservar o aplicativo

- Não mexer no **launcher/ícone de instalação do APK**.
- Não alterar pixels, cores, efeitos 3D, transparência ou proporção dos **20 arquivos PNG originais**; não adicionar fundos roxos nem tint que mude as cores. Ajustar tamanhos no Compose.
- Preservar wallpaper temático do **castelo roxo** e estilo aprovado: cartões 12%, caixas internas 9%, áreas roxas 8%, bordas 15%, desfoque 0 px. Respeitar a semântica existente dos valores no código.
- Preservar integralmente **F2-B** (CardBrand, taxas, cálculos, vendas, Room, migrações, backup, restauração, importação/exportação CSV, navegação, dados do usuário).
- Não alterar `applicationId` `com.mixvendas3d.app`, assinatura, chaves, dependências/build tools sem necessidade comprovada.
- O usuário prefere comandos **organizados e completos**, sem blocos rotulados “Texto simples”, e sem mencionar o modelo Gemini no título das etapas.
- Chaves de assinatura Android: pasta padrão **`C:\Users\olive\OneDrive\Documentos\Chaves`**, arquivo `MixVendas3D.jks`. **Não criar/sobrescrever chaves**; não presumir sincronização do OneDrive. Nesta etapa, não gerar APK Release.

## 5. Referência de tamanhos dos ícones (não é aprovação visual final)

| Contexto | Alvo sugerido, sujeito a layout |
|---|---:|
| Barra inferior, 5 abas | 36 dp |
| Mais, cartões principais | 64 dp |
| Início, cartões | 48–52 dp |
| Nova venda no Início | 40 dp |
| Ações Editar/Excluir | 34–36 dp |
| Busca/Ver mais | 30–34 dp |
| Listas/registros | 34–40 dp |
| Pagamentos | 40–46 dp |

Os PNGs incluem margem alfa interna. Não ampliar cegamente todos os contêineres, não comprometer texto/alinhamento/área de toque. Ajustar apenas onde a arte visível estiver pequena e preservar a responsividade Android. O componente central `Mv3dIcon` está em `ui/components/V19Components.kt` e possui parâmetros `iconRes`, `contentDescription`, `size`, `modifier`, `containerSize` e `alpha`.

## 6. Sequência de trabalho indicada ao Codex

1. **Verifique se há um workspace local mais novo** que o snapshot transferido. Antes de copiar qualquer coisa por cima, compare arquivos e preserve alterações locais, se existirem. Não fazer `git reset --hard` nem `git clean`.
2. Na raiz do projeto, inspecione `git status` (caso exista repositório), Gradle, arquivos Kotlin da cadeia de erros e mudanças visuais. Não reescrever telas inteiras.
3. Execute ` .\gradlew.bat :app:compileDebugKotlin --console=plain --stacktrace ` no Windows (sem os espaços laterais) e **leia o primeiro erro primário**. Se ambiente Java/SDK estiver indisponível, diga claramente; não invente resultados.
4. Corrija referências e causas reais. Verifique `ImportExportViewModel`, `SystemUpdateViewModel`, `SystemUpdateRepository` e `CsvExportValidationException` antes de alterar dependências.
5. Execute ` .\gradlew.bat :app:testDebugUnitTest --console=plain ` e ` .\gradlew.bat :app:assembleDebug --console=plain `. Informar contagem real de testes e resultado.
6. Com build recuperado, revise o dimensionamento dos ícones no código; faça **somente correções pendentes**, priorizando barra inferior, Início, Mais, cadastros e vendas. Preserve layouts e regras de negócio. Se alterar código, repetir testes/build.
7. Entregue relatório conciso com: arquivos alterados, causas dos erros, tamanho de ícones por contexto, testes passados/falhos e caminho real do APK Debug (se criado). **Não gerar Release sem pedido.**

## 7. Mensagem curta para iniciar o Codex

> Leia `CONTEXTO_CODEX.md` e `COMPARACAO_VERSOES.md` primeiro. Este é o snapshot mais novo dos dois ZIPs enviados do MIX VENDAS 3D e contém alterações parciais pós-cancelamento. Não reinicie o projeto. Antes de sobrescrever, compare com o workspace local e preserve o que estiver mais recente. Corrija os cinco erros Kotlin com base no erro primário; as classes indicadas existem no snapshot. Execute testes e build Debug. Depois conclua somente os ajustes de tamanho dos 20 ícones que ainda faltarem, sem modificar imagens PNG, launcher, layout aprovado ou funcionalidades F2-B. Relate o que foi comprovado, não presuma sucesso.

## 8. Arquivos no pacote

- Raiz: projeto Android completo da versão 20:17:44 exportada do AI Studio.
- `CONTEXTO_CODEX.md`: este guia.
- `COMPARACAO_VERSOES.md`: comparação de conteúdo entre snapshots.
- `DIFERENCAS_ENTRE_VERSOES.patch`: diff para consulta, **não reaplicar** no snapshot novo.

Este documento é um resumo de transferência fiel às evidências disponíveis nesta conversa, não uma transcrição integral da branch original privada.
