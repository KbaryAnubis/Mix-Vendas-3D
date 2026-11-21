# Comparação objetiva dos ZIPs — MIX VENDAS 3D

Data do relatório: 19/11/2026.

## Arquivos comparados

- **Anterior**: `mix-vendas-3d(20261119-200053).zip` — SHA-256: `960d18812faea16382234c28d7c10a99a78d4a6fb3a726a96f0bcad7af8b0c09`
- **Mais recente entre os dois enviados**: `mix-vendas-3d (1)(20261119-201744).zip` — SHA-256: `bdbbcd0b9edbd7ad6f5912e1df64260a0eb118b6a2f2d27e70e8a3d58b2e5aac`

O nome da versão nova traz `20261119-201744` e o anterior `20261119-200053`. Esta comparação inspecionou efetivamente os arquivos dentro dos dois ZIPs.

## Resultado

- Entradas de arquivo no anterior: **274**.
- Entradas de arquivo no mais recente: **274**.
- Conteúdos alterados: **16**.
- Arquivos adicionados: **0**.
- Arquivos removidos: **0**.
- Arquivos inalterados: **258**.
- Todas as diferenças ficam dentro de componentes e telas Kotlin relacionados ao ajuste visual dos ícones.
- Verificada presença de **20 imagens** `mv3d_*.png` sob `app/src/main/res/drawable-nodpi/` na versão mais recente e do mapa `public/assets/aistudio/MAPA_DE_ICONES_c4f84ebd.json`.

### Arquivos efetivamente alterados

- `app/src/main/java/com/mixvendas3d/app/ui/components/FechamentoVisualComponents.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/components/ImportExportVisualComponents.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/components/InicioVisualComponents.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/components/MachinesVisualComponents.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/components/MaisVisualComponents.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/components/NamesVisualComponents.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/components/ProductsVisualComponents.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/components/V19Components.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/components/VendasVisualComponents.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/components/VenderVisualComponents.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/screens/MachinesScreen.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/screens/NamesScreen.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/screens/ProductsScreen.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/screens/UpdateSystemScreen.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/screens/VendasScreen.kt`
- `app/src/main/java/com/mixvendas3d/app/ui/screens/VenderScreen.kt`

## Interpretação e limitações

A versão 20:17:44 é a mais atualizada **entre os dois ZIPs entregues aqui**. Não há como afirmar que é idêntica à pasta aberta no PC ou ao último estado do AI Studio sem comparar essa pasta também. A execução de edição foi cancelada após alterar 16 arquivos: o ZIP mais recente já contém justamente essas alterações parciais.

**Não compilamos** o projeto nesta análise e não foi confirmada a resolução dos erros Kotlin. O log de compilação informado pelo usuário permanece uma pendência. As declarações de `ImportExportViewModel`, `SystemUpdateViewModel`, `SystemUpdateRepository` e `CsvExportValidationException` existem no arquivo mais recente: não criar duplicatas, stubs ou substituir funcionalidades sem diagnóstico.

O arquivo `.patch` incluído é um registro das diferenças, não deve ser aplicado automaticamente sobre o ZIP mais recente (as alterações já estão nele).
