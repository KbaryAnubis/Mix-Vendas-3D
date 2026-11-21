package com.mixvendas3d.app.navigation

enum class MainTab(val route: String) {
    INICIO("tab_inicio"),
    VENDER("tab_vender"),
    VENDAS("tab_vendas"),
    FECHAMENTO("tab_fechamento"),
    MAIS("tab_mais"),
}

enum class MaisSubDestination(
    val route: String,
    val title: String,
    val overline: String,
    val summary: String,
    val plannedStageLabel: String,
) {
    TYPES_PRODUCTS(
        route = "mais_products",
        title = "Produtos",
        overline = "CADASTROS E REGRAS",
        summary = "Cadastro real de produtos no Room com preço padrão, divisão direta por participante e responsável pelas tarifas.",
        plannedStageLabel = "Ativo no Room",
    ),
    MACHINES(
        route = "mais_machines",
        title = "Maquininhas",
        overline = "CARTÕES E TARIFAS",
        summary = "Cadastro real de maquininhas no Room com débito, crédito padrão, tarifas por bandeira e adicionais de parcelamento.",
        plannedStageLabel = "Ativo no Room",
    ),
    NAMES(
        route = "mais_names",
        title = "Nomes",
        overline = "PERSONALIZAÇÃO",
        summary = "Cadastro real de pessoas participantes no Room, sem limite fixo de nomes e preservando IDs ao renomear.",
        plannedStageLabel = "Ativo no Room",
    ),
    DAILY_SALES(
        route = "mais_daily_sales",
        title = "Vendas por Dia",
        overline = "HISTÓRICO DIÁRIO",
        summary = "Todos os dias que tiveram vendas, separados por mês. Toque em um dia para abrir suas vendas.",
        plannedStageLabel = "Ativo no Room",
    ),
    IMPORT_EXPORT(
        route = "mais_import_export",
        title = "Importar / exportar",
        overline = "MESCLAGEM SEM EXCLUSÕES",
        summary = "Exportação JSON/backup local e importação com comparação inteligente sem apagar registros existentes.",
        plannedStageLabel = "Backup local",
    ),
    UPDATE_SYSTEM(
        route = "mais_update_system",
        title = "Atualizar sistema",
        overline = "ATUALIZAÇÃO INTERNA DO HISTÓRICO",
        summary = "Analisa ajustes após mudanças em nomes, produtos e maquininhas, exibe prévia antes/depois e atualiza ou desfaz com segurança.",
        plannedStageLabel = "Ativo no Room",
    ),
}

object AppDestination {
    const val ROUTE_STARTUP_DIAGNOSTIC = "startup_diagnostic"
}
